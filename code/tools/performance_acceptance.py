#!/usr/bin/env python3
"""REQ-018: deterministic fixture + 20 authenticated clients / 100 HTML queries."""

import argparse
import concurrent.futures
import csv
import ctypes
import hashlib
from html.parser import HTMLParser
import http.cookiejar
import json
import math
import os
from pathlib import Path
import platform
import re
import socket
import statistics
import sys
import threading
import time
from datetime import date, datetime, timedelta, timezone
import urllib.error
import urllib.parse
import urllib.request


DATABASE = "workshop_acceptance"
CLIENTS = 20
ROUNDS = 5
PAGE_SIZE = 8
BEIJING = timezone(timedelta(hours=8))
TOPICS = ("蓝晒", "声音", "木工", "版画", "陶艺")
LABELS = {"OPEN": "可报名", "WAITLIST": "已满可候补", "CLOSED": "已截止", "CANCELLED": "已取消"}
# This is the public, fictional V002 demo credential, never a personal password.
DEMO_HASH = "$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6"
DEMO_PASSWORD = "Demo-2026"


def json_text(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def write_json(path, value):
    path.write_text(json_text(value), encoding="utf-8")


def fixture(anchor):
    """Declare rows first; SQL serialization and the Python oracle consume these rows."""
    users = [dict(id=i, account=f"perf_{i:02d}", password_hash=DEMO_HASH,
                  display_name=f"验收参与者{i:02d}", role="PARTICIPANT") for i in range(1, 21)]
    users.append(dict(id=21, account="perf_organizer", password_hash=DEMO_HASH,
                      display_name="验收组织者", role="ORGANIZER"))
    activities = []
    for i in range(1, 1001):
        suffix = "100%_体验" if i % 37 == 0 else ""
        activities.append(dict(id=i, name=f"性能验收{TOPICS[(i - 1) % 5]}{i:04d}{suffix}",
                               description=f"独立验收库的虚构工作坊{i:04d}简介。",
                               organizer_id=21, publication_status="PUBLISHED"))
    sessions, registrations = [], []
    for i in range(1, 10001):
        kind = (i - 1) % 5
        start = datetime.combine(anchor + timedelta(days=30 + ((i - 1) // 5) % 120),
                                 datetime.min.time()).replace(hour=14)
        deadline = datetime.combine(anchor + timedelta(days=-7 if kind == 2 else 15),
                                    datetime.min.time()).replace(hour=18)
        sessions.append(dict(id=i, activity_id=(i - 1) // 10 + 1,
                             start_time=str(start), end_time=str(start + timedelta(hours=2)),
                             registration_deadline=str(deadline), location=f"验收教室{i % 50:02d}",
                             capacity=(8, 3, 5, 6, 0)[kind],
                             publication_status="CANCELLED" if kind == 3 else "PUBLISHED",
                             published_before_cancel=kind == 3))
        states = (("REGISTERED",), ("REGISTERED",) * 3 + ("WAITLIST",),
                  ("REGISTERED",) * 2, ("CANCELLED",), ())[kind]
        for offset, state in enumerate(states):
            registrations.append(dict(id=len(registrations) + 1,
                                      participant_id=(i + offset - 1) % 20 + 1, session_id=i,
                                      registered_at=str(datetime.combine(anchor - timedelta(days=10),
                                                                        datetime.min.time())),
                                      status=state, waitlist_order=1 if state == "WAITLIST" else None))
    return dict(users=users, activities=activities, sessions=sessions, registrations=registrations)


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, (bool, int)):
        return str(int(value))
    # Generated fixture strings contain no backslashes; use SQL-standard quote escaping.
    return "'" + value.replace("'", "''") + "'"


def seed(args):
    anchor = date.fromisoformat(args.anchor)
    rows = fixture(anchor)
    args.output.mkdir(parents=True, exist_ok=True)
    sql = ["-- Generated fixture; use only the empty, separately initialized acceptance database.",
           f"USE `{DATABASE}`;", "SET NAMES utf8mb4;", "DELIMITER //",
           "CREATE PROCEDURE acceptance_seed_v1()", "BEGIN",
           "  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;",
           f"  IF DATABASE() <> '{DATABASE}' THEN",
           "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wrong acceptance database';", "  END IF;",
           "  IF (SELECT COUNT(*) FROM users) + (SELECT COUNT(*) FROM activities)",
           "     + (SELECT COUNT(*) FROM sessions) + (SELECT COUNT(*) FROM registrations)",
           "     + (SELECT COUNT(*) FROM operation_logs) <> 0 THEN",
           "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance database must be empty';",
           "  END IF;", "  START TRANSACTION;"]
    for table, records in rows.items():
        columns = list(records[0])
        for start in range(0, len(records), 500):
            values = ["(" + ", ".join(sql_value(row[col]) for col in columns) + ")"
                      for row in records[start:start + 500]]
            sql.append(f"  INSERT INTO {table} ({', '.join(columns)}) VALUES\n    "
                       + ",\n    ".join(values) + ";")
    sql += ["  COMMIT;", "END //", "DELIMITER ;", "CALL acceptance_seed_v1();",
            "DROP PROCEDURE acceptance_seed_v1;", ""]
    sql_bytes = "\n".join(sql).encode("utf-8")
    (args.output / "acceptance-seed.sql").write_bytes(sql_bytes)
    manifest = dict(format_version=1, database=DATABASE, anchor=anchor.isoformat(),
                    counts={table: len(records) for table, records in rows.items()},
                    dataset_sha256=digest(rows), sql_sha256=hashlib.sha256(sql_bytes).hexdigest(),
                    valid_before=(anchor + timedelta(days=14)).isoformat())
    write_json(args.output / "fixture.json", manifest)
    print(json_text(manifest), end="")


class Node:
    def __init__(self, tag="", attrs=()):
        self.tag, self.attrs, self.children = tag, dict(attrs), []

    def text(self):
        return " ".join(" ".join(child.text() if isinstance(child, Node) else child
                                 for child in self.children).split())

    def find(self, tag=None, css=None):
        matches = []
        if (tag is None or self.tag == tag) and (css is None or css in self.attrs.get("class", "").split()):
            matches.append(self)
        for child in self.children:
            if isinstance(child, Node):
                matches.extend(child.find(tag, css))
        return matches


class Document(HTMLParser):
    VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}

    def __init__(self, html):
        super().__init__(convert_charrefs=True)
        self.root = Node()
        self.stack = [self.root]
        self.feed(html)
        self.close()

    def handle_starttag(self, tag, attrs):
        node = Node(tag, attrs)
        self.stack[-1].children.append(node)
        if tag not in self.VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in self.VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        for pos in range(len(self.stack) - 1, 0, -1):
            if self.stack[pos].tag == tag:
                del self.stack[pos:]
                break

    def handle_data(self, data):
        self.stack[-1].children.append(data)


def only(nodes):
    if len(nodes) != 1:
        raise ValueError(f"Expected one HTML element, got {len(nodes)}")
    return nodes[0]


def page_projection(html):
    if not html.rstrip().lower().endswith("</html>"):
        raise ValueError("Incomplete HTML document")
    root = Document(html).root
    total_text = only(root.find(css="list-head")).text()
    total = int(re.search(r"共\s*(\d+)\s*个场次", total_text).group(1))
    pager = root.find(css="pager")
    current, pages = (1, 1)
    if pager:
        current, pages = map(int, only(pager).find("span")[0].text().split("/"))
    rows = []
    for item in root.find("details", "session"):
        status = only(item.find("span", "status"))
        status_code = [code for code in LABELS if code in status.attrs.get("class", "").split()]
        facts = {only(part.find("dt")).text(): only(part.find("dd")).text()
                 for part in only(item.find("dl", "session-facts")).find("div")}
        capacity = re.fullmatch(r"共 (\d+) 个，剩余 (\d+)", facts["名额"])
        rows.append(dict(id=int(item.attrs["id"].removeprefix("session-")),
                         name=only(item.find(css="session-title")).text(),
                         time=only(item.find(css="session-time")).text(),
                         location=only(item.find(css="session-location")).text(),
                         availability=only(status_code), status_label=status.text(),
                         description=only(only(item.find(css="session-about")).find("p")).text(),
                         detail_time=facts["场次时间"], detail_location=facts["活动地点"],
                         deadline=facts["报名截止"], capacity=int(capacity.group(1)),
                         remaining=int(capacity.group(2))))
    return dict(total=total, page=current, pages=pages, rows=rows)


def oracle_rows(data, now):
    """Independent Python evaluation: no SQL queries and no observed HTML as expected data."""
    activities = {row["id"]: row for row in data["activities"]}
    occupancy, waiting = {}, set()
    for row in data["registrations"]:
        if row["status"] in ("REGISTERED", "CHECKED_IN", "ABSENT"):
            occupancy[row["session_id"]] = occupancy.get(row["session_id"], 0) + 1
        elif row["status"] == "WAITLIST":
            waiting.add(row["session_id"])
    result = []
    for session in data["sessions"]:
        activity = activities[session["activity_id"]]
        if activity["publication_status"] not in ("PUBLISHED", "CANCELLED"):
            continue
        cancelled = session["publication_status"] == "CANCELLED"
        if session["publication_status"] != "PUBLISHED" and not (cancelled and session["published_before_cancel"]):
            continue
        start, end, deadline = (datetime.fromisoformat(session[col]) for col in
                                ("start_time", "end_time", "registration_deadline"))
        remaining = session["capacity"] - occupancy.get(session["id"], 0)
        status = ("CANCELLED" if cancelled else "CLOSED" if deadline <= now else
                  "WAITLIST" if remaining <= 0 or session["id"] in waiting else "OPEN")
        clock_range = f"{start:%H:%M}–{end:%H:%M}"
        day_label = f"{start.month}月{start.day}日"
        result.append(dict(id=session["id"], name=activity["name"],
                           time=day_label + " " + clock_range, location=session["location"],
                           availability=status, status_label=LABELS[status],
                           description=activity["description"],
                           detail_time=f"{day_label}（周{'一二三四五六日'[start.weekday()]}）{clock_range}",
                           detail_location=session["location"],
                           deadline=f"{deadline.month}月{deadline.day}日 {deadline:%H:%M}",
                           capacity=session["capacity"], remaining=remaining,
                           _date=start.date().isoformat(), _start=start,
                           _active=status in ("OPEN", "WAITLIST")))
    return sorted(result, key=lambda row: (not row["_active"], row["_start"], row["id"]))


def expected_page(rows, query):
    matches = [row for row in rows if query["keyword"] in row["name"]
               and query["dateFrom"] <= row["_date"] <= query["dateTo"]
               and (not query["availability"] or row["availability"].lower() == query["availability"])]
    pages = max(1, math.ceil(len(matches) / PAGE_SIZE))
    current = min(int(query["page"]), pages)
    visible = [{key: value for key, value in row.items() if not key.startswith("_")}
               for row in matches[(current - 1) * PAGE_SIZE:current * PAGE_SIZE]]
    return dict(total=len(matches), page=current, pages=pages, rows=visible)


def query_for(anchor, user, round_index):
    # 80 three-filter combinations + 20 name/date combinations including cancellations.
    keyword, lower, upper, status, page = "验收", 30, 149, "", 1 + user * 65
    if round_index == 1:
        keyword, status, page = TOPICS[user % 5], ("open", "waitlist", "closed")[user % 3], (1, 2, 99999)[user % 3]
    elif round_index == 2:
        keyword, lower, upper, status, page = "100%_" if user % 4 == 0 else TOPICS[user % 5], 50, 99, ("open", "waitlist", "closed")[user % 3], 2
    elif round_index == 3:
        lower = upper = 30 + user * 5
        status, page = ("open", "waitlist", "closed")[user % 3], 1
    elif round_index == 4:
        keyword, status, page = TOPICS[user % 5], ("open", "waitlist", "closed")[user % 3], 1 + user * 7
    return dict(keyword=keyword, dateFrom=(anchor + timedelta(days=lower)).isoformat(),
                dateTo=(anchor + timedelta(days=upper)).isoformat(), availability=status, page=str(page))


def open_html(opener, url, timeout, data=None):
    request = urllib.request.Request(url, data=data, headers={"User-Agent": "Workshop-REQ018/1.0"})
    with opener.open(request, timeout=timeout) as response:
        body = response.read()  # The timer includes receiving the complete response body.
        return response.status, response.url, body.decode("utf-8"), len(body)


def login(opener, base, user, timeout):
    _, _, html, _ = open_html(opener, base + "/login", timeout)
    csrf = only([node for node in Document(html).root.find("input") if node.attrs.get("name") == "_csrf"])
    data = urllib.parse.urlencode(dict(username=f"perf_{user:02d}", password=DEMO_PASSWORD,
                                       _csrf=csrf.attrs["value"])).encode()
    status, url, html, _ = open_html(opener, base + "/login", timeout, data)
    if status != 200 or urllib.parse.urlsplit(url).path != "/workshops":
        raise ValueError("Login failed")
    if only(Document(html).root.find("span", "name")).text() != f"验收参与者{user:02d}":
        raise ValueError("Authenticated identity mismatch")


def safe_error(exc):
    # Do not serialize response HTML, cookies, CSRF tokens, credentials, or arbitrary error text.
    if isinstance(exc, urllib.error.HTTPError):
        return f"HTTP {exc.code}"
    if isinstance(exc, (TimeoutError, socket.timeout)):
        return "timeout"
    if isinstance(exc, urllib.error.URLError):
        return "connection_error:" + type(exc.reason).__name__
    return type(exc).__name__


def measure(opener, base, query, expected, user, round_index, phase, timeout):
    row = dict(phase=phase, user=f"perf_{user:02d}", round=round_index + 1,
               started_at=datetime.now(timezone.utc).isoformat(), query=urllib.parse.urlencode(query),
               elapsed_ms=0, http_status="", bytes=0, correct=False, within_2s=False,
               qualified=False, error="", expected_total=expected["total"], actual_total="",
               expected_page=expected["page"], actual_page="", expected_pages=expected["pages"], actual_pages="",
               expected_ids=json.dumps([item["id"] for item in expected["rows"]]), actual_ids="",
               expected_statuses=json.dumps([item["availability"] for item in expected["rows"]]), actual_statuses="",
               expected_sha256=digest(expected), actual_sha256="")
    started = time.perf_counter()
    try:
        status, url, html, size = open_html(opener, base + "/workshops?" + row["query"], timeout)
        elapsed = time.perf_counter() - started
        row.update(http_status=status, bytes=size)
        if status != 200 or urllib.parse.urlsplit(url).path != "/workshops":
            row["error"] = "unexpected_status_or_redirect"
        else:
            actual = page_projection(html)
            row.update(actual_total=actual["total"], actual_page=actual["page"], actual_pages=actual["pages"],
                       actual_ids=json.dumps([item["id"] for item in actual["rows"]]),
                       actual_statuses=json.dumps([item["availability"] for item in actual["rows"]]),
                       actual_sha256=digest(actual), correct=actual == expected)
            if not row["correct"]:
                row["error"] = "content_mismatch:" + ",".join(key for key in expected if expected[key] != actual[key])
    except Exception as exc:
        elapsed = time.perf_counter() - started
        row["error"] = safe_error(exc)
        if isinstance(exc, urllib.error.HTTPError):
            row["http_status"] = exc.code
    row.update(elapsed_ms=round(elapsed * 1000, 3), within_2s=elapsed <= 2.0)
    row["qualified"] = row["correct"] and row["within_2s"]
    return row


def memory_bytes():
    if os.name == "nt":
        class MemoryStatus(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
                (name, ctypes.c_ulonglong) for name in ("total", "available", "page_total", "page_available", "virtual_total", "virtual_available", "extended")]
        status = MemoryStatus()
        status.length = ctypes.sizeof(status)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            return status.total
    elif hasattr(os, "sysconf"):
        try:
            return os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES")
        except (OSError, ValueError):
            pass
    return None


def run(args):
    base = os.environ.get("BASE_URL", "").rstrip("/")
    address = urllib.parse.urlsplit(base)
    if (address.scheme != "http" or address.hostname not in ("localhost", "127.0.0.1", "::1")
            or address.username or address.password or address.path or address.query or address.fragment):
        raise ValueError("BASE_URL must be a local HTTP origin, e.g. http://127.0.0.1:18084")
    manifest = json.loads(args.fixture.read_text(encoding="utf-8"))
    if manifest["database"] != DATABASE or manifest["format_version"] != 1:
        raise ValueError("Unexpected fixture database/version")
    anchor = date.fromisoformat(manifest["anchor"])
    now = datetime.now(BEIJING).replace(tzinfo=None)
    if not anchor <= now.date() < date.fromisoformat(manifest["valid_before"]):
        raise ValueError("Fixture outside stable status window; generate and import a fresh fixture")
    data = fixture(anchor)
    if digest(data) != manifest["dataset_sha256"]:
        raise ValueError("Fixture differs from the generator version")
    metadata = json.loads(args.metadata.read_text(encoding="utf-8-sig"))
    for key in ("java_version", "mysql_version", "browser_version"):
        if not isinstance(metadata.get(key), str) or not metadata[key].strip():
            raise ValueError(f"Metadata requires {key}")
    args.output.mkdir(parents=True, exist_ok=True)
    for name in ("environment.json", "summary.json", "requests.csv", "warmup.csv"):
        if (args.output / name).exists():
            raise ValueError("Use a new output directory to preserve previous run evidence")
    environment = dict(started_at=datetime.now(timezone.utc).isoformat(), base_url=base,
                       database=DATABASE, fixture=manifest, os=platform.platform(),
                       cpu=metadata.get("cpu", os.environ.get("PROCESSOR_IDENTIFIER", platform.processor())),
                       logical_cpus=os.cpu_count(), ram_bytes=metadata.get("ram_bytes", memory_bytes()),
                       python=platform.python_version(), java_version=metadata["java_version"],
                       mysql_version=metadata["mysql_version"], browser_version=metadata["browser_version"],
                       network="loopback HTTP; proxies disabled", client="Python urllib; full HTML body; no browser rendering",
                       timeout_seconds=args.timeout, users=CLIENTS, rounds=ROUNDS, timed_requests=CLIENTS * ROUNDS,
                       warmup_requests=CLIENTS, timing="Before HTTP request through complete body read; oracle excluded",
                       synchronization="20 independent cookie jars; barrier before each of 5 rounds",
                       app_revision=metadata.get("app_revision", "not supplied"))
    write_json(args.output / "environment.json", environment)
    oracle = oracle_rows(data, now)
    plans = {(user, rnd): (query := query_for(anchor, user - 1, rnd), expected_page(oracle, query))
             for user in range(1, CLIENTS + 1) for rnd in range(ROUNDS)}
    warm_query = query_for(anchor, 0, 0)
    warm_expected = expected_page(oracle, warm_query)
    openers, login_results, warmup = {}, [], []
    for user in range(1, CLIENTS + 1):
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                                            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        openers[user] = opener
        try:
            login(opener, base, user, args.timeout)
            login_results.append(dict(user=f"perf_{user:02d}", success=True, error=""))
        except Exception as exc:
            login_results.append(dict(user=f"perf_{user:02d}", success=False, error=safe_error(exc)))
        warmup.append(measure(opener, base, warm_query, warm_expected, user, -1, "warmup", args.timeout))
    barrier = threading.Barrier(CLIENTS)

    def worker(user):
        results = []
        for rnd in range(ROUNDS):
            barrier.wait()
            query, expected = plans[user, rnd]
            results.append(measure(openers[user], base, query, expected, user, rnd, "measured", args.timeout))
        return results

    measured = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=CLIENTS) as pool:
        for results in pool.map(worker, range(1, CLIENTS + 1)):
            measured.extend(results)
    measured.sort(key=lambda row: (row["round"], row["user"]))
    for name, results in (("requests.csv", measured), ("warmup.csv", warmup)):
        with (args.output / name).open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(results[0]))
            writer.writeheader()
            writer.writerows(results)
    elapsed = sorted(row["elapsed_ms"] for row in measured)
    qualified = sum(row["qualified"] for row in measured)
    setup_ok = all(item["success"] for item in login_results) and all(row["correct"] for row in warmup)
    summary = dict(requirement="REQ-018", completed_at=datetime.now(timezone.utc).isoformat(),
                   passed=setup_ok and len(measured) == 100 and qualified >= 95, setup_ok=setup_ok,
                   total_requests=len(measured), correct=sum(row["correct"] for row in measured),
                   qualified_within_2s=qualified, required_qualified=95,
                   incorrect_or_failed=sum(not row["correct"] for row in measured),
                   over_2s=sum(not row["within_2s"] for row in measured),
                   elapsed_ms=dict(min=elapsed[0], median=statistics.median(elapsed),
                                   p95_nearest_rank=elapsed[math.ceil(len(elapsed) * .95) - 1], max=elapsed[-1]),
                   login_results=login_results, warmup_correct=sum(row["correct"] for row in warmup),
                   calculation="All 100 outcomes retained; only complete, exact oracle matches within 2 seconds qualify")
    write_json(args.output / "summary.json", summary)
    print(json_text(summary), end="")
    return 0 if summary["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    generate = commands.add_parser("seed", help="Write SQL and fixture manifest; never connects to MySQL")
    generate.add_argument("--output", type=Path, required=True)
    generate.add_argument("--anchor", default=datetime.now(BEIJING).date().isoformat())
    execute = commands.add_parser("run", help="Run authenticated HTTP acceptance using BASE_URL")
    execute.add_argument("--fixture", type=Path, required=True)
    execute.add_argument("--metadata", type=Path, required=True)
    execute.add_argument("--output", type=Path, required=True)
    execute.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()
    if args.command == "run" and args.timeout <= 0:
        parser.error("--timeout must be positive")
    return seed(args) if args.command == "seed" else run(args)


if __name__ == "__main__":
    sys.exit(main())
