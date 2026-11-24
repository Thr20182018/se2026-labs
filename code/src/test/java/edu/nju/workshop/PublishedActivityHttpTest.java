package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.nju.workshop.activity.PublishedActivityService;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class PublishedActivityHttpTest extends WorkshopTestBase {

  @Autowired
  private PublishedActivityService published;

  private MockHttpSession organizer;
  private MockHttpSession otherOrganizer;
  private MockHttpSession admin;
  private MockHttpSession participant;

  @BeforeEach
  void publishedActivity() throws Exception {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "zhao_ming", "赵明远", "ORGANIZER");
    user(4, "he_qm", "何启明", "ADMIN");
    user(5, "lu_xi", "陆溪", "ADMIN");
    organizer = login("su_qing", "ORGANIZER");
    otherOrganizer = login("zhao_ming", "ORGANIZER");
    admin = login("he_qm", "ADMIN");
    participant = login("lin_zy", "PARTICIPANT");
    clock.setBeijing(LocalDateTime.of(2026, 11, 18, 10, 0));
    activity(10, 2, "原版蓝晒工作坊", "PUBLISHED", null, null);
    session(10, 10, "2026-11-20 14:00:00", "2026-11-20 16:00:00",
        "2026-11-20 14:00:00", 8, "PUBLISHED");
  }

  @Test
  void revisionKeepsPublishedContentUntilApprovalAndRecordsBothValues() throws Exception {
    submitRevision("新版蓝晒工作坊");
    assertEquals("PUBLISHED", state("activities", 10));
    assertEquals("PUBLISHED", state("sessions", 10));
    String publicPage = body(participant, "/workshops");
    assertTrue(publicPage.contains("原版蓝晒工作坊"));
    assertFalse(publicPage.contains("新版蓝晒工作坊"));
    assertTrue(body(organizer, "/organizer/activities/10").contains("新版蓝晒工作坊"));
    assertTrue(body(organizer, "/organizer/activities").contains("内容修改：待审核"));
    assertTrue(body(admin, "/admin/reviews/10/revision").contains("新版蓝晒工作坊"));
    decide("/revision", "approve", version("activities", 10));
    publicPage = body(participant, "/workshops");
    assertTrue(publicPage.contains("新版蓝晒工作坊"));
    assertTrue(publicPage.contains("新版蓝晒工作坊的制作说明"));
    assertFalse(publicPage.contains("原版蓝晒工作坊"));
    assertTrue(body(organizer, "/organizer/activities").contains("内容修改：已发布"));
    assertRevisionCleared();
    var change = jdbc.queryForMap("SELECT before_value, after_value FROM operation_logs"
        + " WHERE action = 'APPROVE_REVISION' AND result = 'SUCCESS'");
    assertTrue(change.get("before_value").toString().contains("简介：原版蓝晒工作坊"));
    assertTrue(change.get("after_value").toString().contains("新版蓝晒工作坊的制作说明"));
    assertEquals(1, logs("SUBMIT_REVISION", "SUCCESS"));
    assertEquals(1, logs("APPROVE_REVISION", "SUCCESS"));
  }

  @Test
  void rejectedRevisionIsDiscardedAndOldVersionCannotReviewResubmission() throws Exception {
    submitRevision("驳回版名称");
    long oldVersion = version("activities", 10);
    decide("/revision", "reject", oldVersion);
    assertRevisionCleared();
    assertTrue(body(participant, "/workshops").contains("原版蓝晒工作坊"));
    String organizerPage = body(organizer, "/organizer/activities/10");
    assertFalse(organizerPage.contains("驳回版名称"));
    assertTrue(organizerPage.contains("本次内容修改已驳回，当前公开内容保持不变，可修改后重新提交。"));
    assertTrue(body(organizer, "/organizer/activities").contains("内容修改：已驳回"));
    submitRevision("重提版名称");
    assertFalse(body(organizer, "/organizer/activities/10").contains("本次内容修改已驳回"));
    assertTrue(body(organizer, "/organizer/activities").contains("内容修改：待审核"));
    assertEquals(oldVersion + 1, version("activities", 10));
    mvc.perform(review(admin, "/revision", "approve", oldVersion))
        .andExpect(status().isConflict());
    assertEquals(1, count("SELECT revision_pending FROM activities WHERE id = 10"));
    assertFalse(body(participant, "/workshops").contains("重提版名称"));
    decide("/revision", "approve", oldVersion + 1);
    assertTrue(body(participant, "/workshops").contains("重提版名称"));
    assertTrue(body(organizer, "/organizer/activities").contains("内容修改：已发布"));
    assertEquals(1, logs("REJECT_REVISION", "SUCCESS"));
    assertEquals(1, logs("APPROVE_REVISION", "FAILURE"));
  }

  @Test
  void organizerListSummarizesOnlySubmittedNewSessions() throws Exception {
    String list = body(organizer, "/organizer/activities");
    assertFalse(list.contains("内容修改："));
    assertFalse(list.contains("新增场次："));
    addSession("尚未提交的草稿");
    assertFalse(body(organizer, "/organizer/activities").contains("新增场次："));
    long first = addSession("第一待审场次");
    long second = addSession("第二待审场次");
    long rejected = addSession("驳回场次");
    long published = addSession("通过场次");
    for (long id : new long[] {first, second, rejected, published}) {
      submitSession(id);
    }
    decide("/sessions/" + rejected, "reject", version("sessions", rejected));
    decide("/sessions/" + published, "approve", version("sessions", published));
    list = body(organizer, "/organizer/activities");
    assertTrue(list.contains("活动：已发布"));
    assertTrue(list.contains("新增场次："));
    assertTrue(list.contains("待审核 2"));
    assertTrue(list.contains("已驳回 1"));
    assertTrue(list.contains("已发布 1"));
    assertFalse(list.contains("已发布 2"));
    submitSession(rejected);
    list = body(organizer, "/organizer/activities");
    assertTrue(list.contains("待审核 3"));
    assertFalse(list.contains("已驳回 1"));
  }

  @Test
  void newSessionRemainsHiddenUntilApprovalAndOriginalSessionIsPreserved() throws Exception {
    long sessionId = addSession("新增教室 B201");
    assertEquals("DRAFT", state("sessions", sessionId));
    assertFalse(body(participant, "/workshops").contains("新增教室 B201"));
    submitSession(sessionId);
    assertFalse(body(participant, "/workshops").contains("新增教室 B201"));
    mvc.perform(sessionForm(organizer, "/" + sessionId, "审核中改地点"))
        .andExpect(status().isConflict());
    decide("/sessions/" + sessionId, "approve", version("sessions", sessionId));
    String publicPage = body(participant, "/workshops");
    assertTrue(publicPage.contains("新增教室 B201"));
    assertTrue(publicPage.contains("逸夫楼 A203"));
    assertEquals("PUBLISHED", state("sessions", 10));
    assertEquals("PUBLISHED", state("sessions", sessionId));
    mvc.perform(sessionForm(organizer, "/10", "发布后修改原地点"))
        .andExpect(status().isConflict());
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'PUBLISH_SESSION'"
        + " AND actor_type = 'SYSTEM' AND actor_id IS NULL AND session_id = ?", sessionId));
  }

  @Test
  void rejectedSessionCanBeEditedAndResubmittedWithItsOwnVersion() throws Exception {
    long sessionId = addSession("被驳回教室");
    submitSession(sessionId);
    long oldVersion = version("sessions", sessionId);
    decide("/sessions/" + sessionId, "reject", oldVersion);
    assertEquals("REJECTED", state("sessions", sessionId));
    assertFalse(body(participant, "/workshops").contains("被驳回教室"));
    mvc.perform(sessionForm(organizer, "/" + sessionId, "修改后教室"))
        .andExpect(status().is3xxRedirection());
    submitSession(sessionId);
    assertEquals(oldVersion + 1, version("sessions", sessionId));
    mvc.perform(review(admin, "/sessions/" + sessionId, "reject", oldVersion))
        .andExpect(status().isConflict());
    assertEquals("PENDING", state("sessions", sessionId));
    decide("/sessions/" + sessionId, "approve", oldVersion + 1);
    assertTrue(body(participant, "/workshops").contains("修改后教室"));
    assertEquals("PUBLISHED", state("sessions", 10));
    assertEquals(1, logs("REJECT_SESSION_REVIEW", "FAILURE"));
  }

  @Test
  void concurrentSessionDecisionLeavesOtherRequestsIndependent() throws Exception {
    submitRevision("独立审核新名称");
    long first = addSession("并发审核教室");
    long second = addSession("独立审核教室");
    submitSession(first);
    submitSession(second);
    activity(30, 2, "首次申请活动", "PENDING", null, null);
    session(30, 30, "2026-11-22 14:00:00", "2026-11-22 16:00:00",
        "2026-11-22 14:00:00", 8, "PENDING");
    String queue = body(admin, "/admin/reviews");
    assertTrue(queue.contains("/admin/reviews/30"));
    assertTrue(queue.contains("/admin/reviews/10/revision"));
    assertTrue(queue.contains("/admin/reviews/10/sessions/" + first));
    assertTrue(queue.contains("/admin/reviews/10/sessions/" + second));
    final long revisionVersion = version("activities", 10);
    long sessionVersion = version("sessions", first);
    MockHttpSession secondAdmin = login("lu_xi", "ADMIN");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var approval = executor.submit(() -> concurrentDecision(admin, first, "approve",
          sessionVersion, ready, start));
      var rejection = executor.submit(() -> concurrentDecision(secondAdmin, first, "reject",
          sessionVersion, ready, start));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      int approved = approval.get(10, TimeUnit.SECONDS);
      int rejected = rejection.get(10, TimeUnit.SECONDS);
      assertTrue((approved / 100 == 3 && rejected == 409)
          || (approved == 409 && rejected / 100 == 3));
      assertEquals(approved / 100 == 3 ? "PUBLISHED" : "REJECTED", state("sessions", first));
    } finally {
      start.countDown();
    }
    assertEquals(1, logs("APPROVE_SESSION_REVIEW", "SUCCESS")
        + logs("REJECT_SESSION_REVIEW", "SUCCESS"));
    assertEquals("PENDING", state("sessions", second));
    assertEquals(revisionVersion, version("activities", 10));
    decide("/revision", "approve", revisionVersion);
    assertEquals("PENDING", state("sessions", second));
    decide("/sessions/" + second, "approve", version("sessions", second));
    assertEquals("PENDING", state("activities", 30));
    assertEquals("PUBLISHED", state("sessions", 10));
  }

  @Test
  void maintenanceRequiresTheOwningOrganizerAndAnAdministratorForReview() throws Exception {
    long sessionId = addSession("归属校验教室");
    mvc.perform(write(otherOrganizer, "/organizer/activities/10/revision")
            .param("name", "越权修改").param("description", "越权修改"))
        .andExpect(status().isForbidden());
    mvc.perform(write(otherOrganizer, "/organizer/activities/10/sessions/" + sessionId + "/submit"))
        .andExpect(status().isForbidden());
    mvc.perform(sessionForm(otherOrganizer, "/" + sessionId, "越权改地点"))
        .andExpect(status().isForbidden());
    submitRevision("待审核名称");
    submitSession(sessionId);
    mvc.perform(review(organizer, "/revision", "approve", version("activities", 10)))
        .andExpect(status().isForbidden());
    mvc.perform(review(participant, "/sessions/" + sessionId, "approve",
            version("sessions", sessionId))).andExpect(status().isForbidden());
    mvc.perform(post("/admin/reviews/10/revision").session(admin).param("decision", "approve")
            .param("reviewVersion", Long.toString(version("activities", 10))))
        .andExpect(status().isForbidden());
    mvc.perform(post("/admin/reviews/10/sessions/" + sessionId).session(admin)
            .param("decision", "approve")
            .param("reviewVersion", Long.toString(version("sessions", sessionId))))
        .andExpect(status().isForbidden());
    activity(30, 2, "另一活动", "PUBLISHED", null, null);
    session(30, 30, "2026-11-22 14:00:00", "2026-11-22 16:00:00",
        "2026-11-22 14:00:00", 8, "DRAFT");
    mvc.perform(write(organizer, "/organizer/activities/10/sessions/30/submit"))
        .andExpect(status().isForbidden());
    assertEquals("DRAFT", state("sessions", 30));
    assertEquals("PENDING", state("sessions", sessionId));
    assertEquals(0, logs("APPROVE_REVISION", "SUCCESS"));
    assertEquals(0, logs("PUBLISH_SESSION", "SUCCESS"));
    assertEquals(2, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'APPROVE_REVISION'"
        + " AND result = 'FAILURE' AND activity_id = 10 AND session_id IS NULL"));
    assertEquals(2, count("SELECT COUNT(*) FROM operation_logs"
        + " WHERE action = 'APPROVE_SESSION_REVIEW' AND result = 'FAILURE'"
        + " AND activity_id = 10 AND session_id = ?", sessionId));
  }

  @Test
  void sessionPublicationLogFailureRollsBackDecisionAndAllowsRetry() throws Exception {
    long sessionId = addSession("回滚校验教室");
    submitSession(sessionId);
    long version = version("sessions", sessionId);
    jdbc.execute("ALTER TABLE operation_logs ADD CONSTRAINT test_reject_session_publication"
        + " CHECK (action <> 'PUBLISH_SESSION')");
    try {
      assertThrows(RuntimeException.class,
          () -> published.reviewSession(4, 10, sessionId, "approve", version));
    } finally {
      jdbc.execute("ALTER TABLE operation_logs DROP CHECK test_reject_session_publication");
    }
    assertEquals("PENDING", state("sessions", sessionId));
    assertEquals("PUBLISHED", state("sessions", 10));
    assertEquals(0, logs("APPROVE_SESSION_REVIEW", "SUCCESS"));
    assertEquals(0, logs("PUBLISH_SESSION", "SUCCESS"));
    assertEquals(1, logs("APPROVE_SESSION_REVIEW", "FAILURE"));
    assertFalse(body(participant, "/workshops").contains("回滚校验教室"));
    decide("/sessions/" + sessionId, "approve", version);
    assertEquals("PUBLISHED", state("sessions", sessionId));
  }

  private void submitRevision(String name) throws Exception {
    mvc.perform(write(organizer, "/organizer/activities/10/revision")
            .param("name", name).param("description", name + "的制作说明"))
        .andExpect(status().is3xxRedirection());
  }

  private long addSession(String location) throws Exception {
    mvc.perform(sessionForm(organizer, "", location)).andExpect(status().is3xxRedirection());
    return jdbc.queryForObject("SELECT MAX(id) FROM sessions WHERE activity_id = 10", Long.class);
  }

  private void submitSession(long sessionId) throws Exception {
    mvc.perform(write(organizer, "/organizer/activities/10/sessions/" + sessionId + "/submit"))
        .andExpect(status().is3xxRedirection());
  }

  private void decide(String target, String decision, long version) throws Exception {
    mvc.perform(review(admin, target, decision, version)).andExpect(status().is3xxRedirection());
  }

  private int concurrentDecision(MockHttpSession reviewer, long sessionId, String decision,
      long version, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    return mvc.perform(review(reviewer, "/sessions/" + sessionId, decision, version))
        .andReturn().getResponse().getStatus();
  }

  private MockHttpServletRequestBuilder review(MockHttpSession reviewer, String target,
      String decision, long version) {
    return write(reviewer, "/admin/reviews/10" + target).param("decision", decision)
        .param("reviewVersion", Long.toString(version));
  }

  private MockHttpServletRequestBuilder sessionForm(MockHttpSession actor, String suffix,
      String location) {
    return write(actor, "/organizer/activities/10/sessions" + suffix)
        .param("startTime", "2026-11-21T14:00").param("endTime", "2026-11-21T16:00")
        .param("deadline", "2026-11-21T14:00").param("capacity", "9").param("location", location);
  }

  private MockHttpServletRequestBuilder write(MockHttpSession actor, String path) {
    return post(path).session(actor).with(csrf());
  }

  private long version(String table, long id) {
    return jdbc.queryForObject("SELECT review_version FROM " + table + " WHERE id = ?",
        Long.class, id);
  }

  private String state(String table, long id) {
    return jdbc.queryForObject("SELECT publication_status FROM " + table + " WHERE id = ?",
        String.class, id);
  }

  private int logs(String action, String result) {
    return count("SELECT COUNT(*) FROM operation_logs WHERE action = ? AND result = ?",
        action, result);
  }

  private int count(String sql, Object... arguments) {
    return jdbc.queryForObject(sql, Integer.class, arguments);
  }

  private void assertRevisionCleared() {
    assertEquals(0, count("SELECT revision_pending FROM activities WHERE id = 10"));
    assertNull(jdbc.queryForObject("SELECT pending_name FROM activities WHERE id = 10",
        String.class));
    assertNull(jdbc.queryForObject("SELECT pending_description FROM activities WHERE id = 10",
        String.class));
  }
}
