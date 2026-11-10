package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import edu.nju.workshop.activity.ActivityQuery;
import edu.nju.workshop.activity.ActivityQueryService;
import edu.nju.workshop.activity.Availability;
import edu.nju.workshop.activity.QueryPage;
import edu.nju.workshop.activity.SessionRow;

class SchemaAndDemoTest extends WorkshopTestBase {

  @Autowired
  ActivityQueryService service;

  @Test
  void accountIsUnique() {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    assertThrows(DataIntegrityViolationException.class,
        () -> user(2, "lin_zy", "另一个人", "ORGANIZER"));
  }

  @Test
  void cancelledHistoryCanCoexistAndOnlyOneActiveRecord() {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    activity(1, 2, "蓝晒", "PUBLISHED", null, null);
    session(1, 1, "2026-11-14 14:00:00", "2026-11-14 16:00:00", "2026-11-13 18:00:00",
        8, "PUBLISHED");
    registration(1, 1, 1, "CANCELLED");
    registration(2, 1, 1, "CANCELLED");
    registration(3, 1, 1, "REGISTERED");
    assertThrows(DataIntegrityViolationException.class,
        () -> registration(4, 1, 1, "WAITLIST"));
    jdbc.update("UPDATE registrations SET status = 'CANCELLED' WHERE id = 3");
    registration(5, 1, 1, "WAITLIST");
    Integer cancelled = jdbc.queryForObject(
        "SELECT COUNT(*) FROM registrations WHERE active_pair IS NULL", Integer.class);
    assertEquals(3, cancelled);
  }

  @Test
  void sessionTimeOrderIsEnforcedAndFailureLogNeedsNoTarget() {
    user(1, "su_qing", "苏清和", "ORGANIZER");
    activity(1, 1, "蓝晒", "PUBLISHED", null, null);
    DataAccessException rejected = assertThrows(DataAccessException.class, () -> session(
        1, 1, "2026-11-14 14:00:00", "2026-11-14 16:00:00", "2026-11-14 19:00:00",
        8, "PUBLISHED"));
    assertTrue(rejected.getMessage().contains("ck_sessions_time"));
    jdbc.update(
        "INSERT INTO operation_logs (operated_at, actor_type, activity_id, action, result,"
            + " failure_reason) VALUES (?, 'SYSTEM', 99999, 'REVIEW', 'FAILURE', '目标不存在')",
        LocalDateTime.of(2026, 11, 10, 10, 0));
    Integer logs = jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs", Integer.class);
    assertEquals(1, logs);
  }

  @Test
  void demoSeedLoadsAndPublicQueryUsesPublishedContent() {
    executeScript("db/V002__demo.sql");
    String hash = jdbc.queryForObject(
        "SELECT password_hash FROM users WHERE account = 'lin_zy'", String.class);
    assertTrue(new BCryptPasswordEncoder().matches(PASSWORD, hash));
    clock.setBeijing(LocalDateTime.of(2026, 11, 10, 10, 0));
    QueryPage page = service.search(ActivityQuery.parse(null, null, null, null, null), 20);
    assertEquals(6, page.total());
    assertEquals(Availability.OPEN, row(page, "蓝晒").availability());
    assertEquals(6, row(page, "蓝晒").remaining());
    assertEquals(Availability.WAITLIST, row(page, "声音采集").availability());
    assertEquals(0, row(page, "声音采集").remaining());
    assertEquals(Availability.CLOSED, row(page, "旧物修复").availability());
    assertEquals(4, row(page, "旧物修复").remaining());
    assertEquals(Availability.CANCELLED, row(page, "微型生态瓶").availability());
    SessionRow print = row(page, "版画体验");
    assertEquals("用公开简介介绍一版一色的拓印步骤。", print.description());
    String text = page.rows().stream().map(SessionRow::name).reduce("", (a, b) -> a + b);
    assertFalse(text.contains("待审核名称不应出现"));
    assertFalse(text.contains("草稿木工不应出现"));
    assertFalse(text.contains("待审核陶艺不应出现"));
    assertFalse(text.contains("驳回书法不应出现"));
  }

  private static SessionRow row(QueryPage page, String name) {
    return page.rows().stream()
        .filter(item -> item.name().contains(name))
        .findFirst()
        .orElseThrow();
  }
}
