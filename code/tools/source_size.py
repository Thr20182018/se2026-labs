#!/usr/bin/env python3
"""Count nonblank source lines in explicit project paths using only the standard library."""

import argparse
import hashlib
import json
from pathlib import Path


CODE_ROOT = Path(__file__).resolve().parents[1]
# Explicit paths keep docs, performance data, credentials and build output out of the count.
CATEGORIES = {
    "java_production": ("Java 生产代码", ["src/main/java/**/*.java"]),
    "java_test": ("Java 测试及测试辅助代码", ["src/test/java/**/*.java"]),
    "templates": ("Thymeleaf / HTML 模板", ["src/main/resources/templates/**/*.html"]),
    "javascript": ("JavaScript", ["src/main/resources/static/js/**/*.js"]),
    "css": ("CSS", ["src/main/resources/static/css/**/*.css"]),
    "sql": ("数据库结构、迁移和演示数据 SQL", ["src/main/resources/db/**/*.sql"]),
    "configuration": (
        "构建、运行和风格配置",
        ["pom.xml", "src/main/resources/application.yml", "config/checkstyle/checkstyle.xml"],
    ),
    "python_tools": ("Python 验收与统计工具", ["tools/*.py"]),
    "generated_wrapper": (
        "Maven Wrapper 工具生成文件",
        ["mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties"],
    ),
}
APPLICATION = ["java_production", "templates", "javascript", "css", "sql", "configuration"]


def measure(path):
    text = path.read_text(encoding="utf-8-sig")
    return {
        "path": path.relative_to(CODE_ROOT).as_posix(),
        "nonblank_lines": sum(bool(line.strip()) for line in text.splitlines()),
        # Universal newline decoding makes this identity stable across Windows/Linux checkouts.
        "normalized_utf8_sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(),
    }


def totals(categories, names):
    return {
        "categories": names,
        "files": sum(categories[name]["files"] for name in names),
        "nonblank_lines": sum(categories[name]["nonblank_lines"] for name in names),
    }


def collect():
    categories = {}
    seen = set()
    for key, (label, patterns) in CATEGORIES.items():
        paths = sorted({path for pattern in patterns for path in CODE_ROOT.glob(pattern)
                        if path.is_file()})
        duplicates = seen.intersection(paths)
        if duplicates:
            raise ValueError(f"Files classified twice: {duplicates}")
        seen.update(paths)
        entries = [measure(path) for path in paths]
        categories[key] = {
            "label": label,
            "patterns": patterns,
            "files": len(entries),
            "nonblank_lines": sum(entry["nonblank_lines"] for entry in entries),
            "entries": entries,
        }
    return {
        "schema_version": 1,
        "root": "code/",
        "basis": "Current working-tree files matching the listed patterns, including uncommitted edits.",
        "metric": "Nonblank physical lines after UTF-8 decoding; comments and punctuation-only lines count.",
        "hash_metric": "SHA-256 after removing an optional UTF-8 BOM and normalizing line endings to LF.",
        "excluded": [
            "docs/** (Markdown, screenshots, performance CSV/JSON and this output)",
            "target/**, .local/**, logs/**, local credentials and dependency caches",
            "src/main/resources/static/images/** (existing image assets, including SVG)",
            "README.md, tools/README.md and all other files outside the explicit patterns",
        ],
        "categories": categories,
        "totals": {
            "application": totals(categories, APPLICATION),
            "java_tests": totals(categories, ["java_test"]),
            "support_tools": totals(categories, ["python_tools"]),
            "generated": totals(categories, ["generated_wrapper"]),
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("docs/t7/source-size.json"),
                        help="Output JSON path, relative to code/ unless absolute.")
    args = parser.parse_args()
    output = args.output if args.output.is_absolute() else CODE_ROOT / args.output
    report = collect()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for key, category in report["categories"].items():
        print(f"{key}: {category['files']} files, "
              f"{category['nonblank_lines']} nonblank lines")
    print(f"JSON: {output}")


if __name__ == "__main__":
    main()
