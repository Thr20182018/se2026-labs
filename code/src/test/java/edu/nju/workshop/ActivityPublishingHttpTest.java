package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.nju.workshop.activity.ActivityManagementService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ActivityPublishingHttpTest extends WorkshopTestBase {

  @Autowired
  private ActivityManagementService management;

  private MockHttpSession organizer;
  private MockHttpSession otherOrganizer;
  private MockHttpSession admin;
  private MockHttpSession participant;

  @BeforeEach
  void accounts() throws Exception {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "zhao_ming", "赵明远", "ORGANIZER");
    user(4, "he_qm", "何启明", "ADMIN");
    user(5, "lu_xi", "陆溪", "ADMIN");
    organizer = login("su_qing", "ORGANIZER");
    otherOrganizer = login("zhao_ming", "ORGANIZER");
    admin = login("he_qm", "ADMIN");
    participant = login("lin_zy", "PARTICIPANT");
    clock.setBeijing(LocalDateTime.of(2026, 11, 13, 10, 0));
  }

  @Test
  void twoSessionsPublishTogetherAfterAdministratorApproval() throws Exception {
    long id = createActivity("蓝晒校内工作坊");
    addSession(id, "2026-11-15");
    addSession(id, "2026-11-16");
    assertState(id, "DRAFT", 2);
    assertEquals(2L, jdbc.queryForObject(
        "SELECT organizer_id FROM activities WHERE id = ?", Long.class, id));
    assertTrue(body(organizer, "/organizer/activities").contains("蓝晒校内工作坊"));
    assertFalse(body(otherOrganizer, "/organizer/activities").contains("蓝晒校内工作坊"));
    assertFalse(body(participant, "/workshops").contains("蓝晒校内工作坊"));

    submit(id);
    assertState(id, "PENDING", 2);
    assertTrue(body(admin, "/admin/reviews").contains("蓝晒校内工作坊"));
    String reviewPage = body(admin, "/admin/reviews/" + id);
    assertTrue(reviewPage.contains("name=\"reviewVersion\" value=\"1\""));
    assertTrue(reviewPage.contains("2026-11-16") || reviewPage.contains("11月16日"));
    assertFalse(body(participant, "/workshops").contains("蓝晒校内工作坊"));
    review(id, "approve");
    assertState(id, "PUBLISHED", 2);
    String published = body(participant, "/workshops");
    assertTrue(published.contains("蓝晒校内工作坊"));
    assertTrue(published.contains("11月15日"));
    assertTrue(published.contains("11月16日"));
    assertFalse(body(admin, "/admin/reviews").contains("蓝晒校内工作坊"));

    assertEquals(1, logs("CREATE_ACTIVITY", "SUCCESS"));
    assertEquals(2, logs("CREATE_SESSION", "SUCCESS"));
    assertEquals(1, logs("SUBMIT_REVIEW", "SUCCESS"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'APPROVE_REVIEW'"
        + " AND result = 'SUCCESS' AND actor_type = 'USER' AND actor_id = 4"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'PUBLISH_ACTIVITY'"
        + " AND result = 'SUCCESS' AND actor_type = 'SYSTEM' AND actor_id IS NULL"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE operated_at IS NULL"));
  }

  @Test
  void rejectionAllowsEditingAndResubmissionWithBeforeAndAfterLogs() throws Exception {
    long id = createActivity("旧版植物拓印");
    long sessionId = addSession(id, "2026-11-15");
    submit(id);
    review(id, "reject");
    assertState(id, "REJECTED", 1);
    mvc.perform(write(organizer, activityPath(id)).param("name", "新版植物拓印")
            .param("description", "补充工具与制作步骤"))
        .andExpect(status().is3xxRedirection());
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions/" + sessionId,
            "2026-11-17T14:00", "2026-11-17T16:00", "2026-11-17T14:00", "12", "创作室 B201"))
        .andExpect(status().is3xxRedirection());
    submit(id);
    review(id, "approve");
    assertState(id, "PUBLISHED", 1);
    String published = body(participant, "/workshops");
    assertTrue(published.contains("新版植物拓印"));
    assertTrue(published.contains("补充工具与制作步骤"));
    assertTrue(published.contains("创作室 B201"));
    assertFalse(published.contains("旧版植物拓印"));
    assertEquals(1, logs("REJECT_REVIEW", "SUCCESS"));
    assertEquals(2, logs("SUBMIT_REVIEW", "SUCCESS"));
    var change = jdbc.queryForMap("SELECT before_value, after_value FROM operation_logs"
        + " WHERE action = 'UPDATE_ACTIVITY' AND result = 'SUCCESS'");
    assertTrue(change.get("before_value").toString().contains("旧版植物拓印"));
    assertTrue(change.get("after_value").toString().contains("新版植物拓印"));
    var sessionChange = jdbc.queryForMap("SELECT before_value, after_value FROM operation_logs"
        + " WHERE action = 'UPDATE_SESSION' AND result = 'SUCCESS'");
    assertTrue(sessionChange.get("before_value").toString().contains("A203"));
    assertTrue(sessionChange.get("after_value").toString().contains("B201"));
    assertFalse(change.toString().contains(PASSWORD));
    assertFalse(change.toString().contains(HASH));
  }

  @Test
  void tamperedActivityAndSessionIdsCannotChangeAnotherOrganizersData() throws Exception {
    long id = createActivity("本人负责的活动");
    long ownSession = addSession(id, "2026-11-15");
    activity(100, 3, "别人的私有活动", "DRAFT", null, null);
    session(100, 100, "2026-11-18 14:00:00", "2026-11-18 16:00:00",
        "2026-11-17 18:00:00", 8, "DRAFT");
    MvcResult denied = mvc.perform(write(organizer, activityPath(100))
            .param("name", "越权覆盖").param("description", "越权修改"))
        .andExpect(status().isForbidden()).andReturn();
    assertFalse(html(denied).contains("别人的私有活动"));
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions/100",
            "2026-11-19T14:00", "2026-11-19T16:00", "2026-11-18T18:00", "9", "被篡改地点"))
        .andExpect(status().isForbidden());
    mvc.perform(write(otherOrganizer, activityPath(id) + "/submit"))
        .andExpect(status().isForbidden());
    mvc.perform(write(organizer, activityPath(999)).param("name", "无目标")
            .param("description", "不存在的活动"))
        .andExpect(status().isForbidden());
    assertEquals("别人的私有活动", jdbc.queryForObject(
        "SELECT name FROM activities WHERE id = 100", String.class));
    assertEquals(100L, jdbc.queryForObject(
        "SELECT activity_id FROM sessions WHERE id = 100", Long.class));
    assertEquals("仙林校区 · 逸夫楼 A203", jdbc.queryForObject(
        "SELECT location FROM sessions WHERE id = 100", String.class));
    assertEquals("DRAFT", state("sessions", ownSession));
    assertEquals("DRAFT", state("activities", id));
    assertEquals(2, logs("UPDATE_ACTIVITY", "FAILURE"));
    assertEquals(1, logs("UPDATE_SESSION", "FAILURE"));
    assertEquals(1, logs("SUBMIT_REVIEW", "FAILURE"));
  }

  @Test
  void rolesCsrfAndSelfReviewAreEnforced() throws Exception {
    MvcResult wrongRole = mvc.perform(write(participant, "/organizer/activities")
            .param("name", "越权创建")
            .param("description", "越权创建"))
        .andExpect(status().isForbidden()).andReturn();
    mvc.perform(post("/organizer/activities").session(organizer).param("name", "缺少令牌")
            .param("description", "缺少令牌"))
        .andExpect(status().isForbidden());
    assertEquals(0, count("SELECT COUNT(*) FROM activities"));
    assertEquals(2, logs("CREATE_ACTIVITY", "FAILURE"));
    activity(100, 4, "审核人自己的活动", "PENDING", null, null);
    session(100, 100, "2026-11-15 14:00:00", "2026-11-15 16:00:00",
        "2026-11-14 18:00:00", 8, "PENDING");
    mvc.perform(write(admin, "/admin/reviews/100").param("decision", "approve"))
        .andExpect(status().isForbidden());
    mvc.perform(write(organizer, "/admin/reviews/100").param("decision", "approve"))
        .andExpect(status().isForbidden());
    assertState(100, "PENDING", 1);
    assertEquals(0, logs("PUBLISH_ACTIVITY", "SUCCESS"));
    assertEquals(2, logs("APPROVE_REVIEW", "FAILURE"));
    String reasons = String.join("\n", jdbc.queryForList(
        "SELECT failure_reason FROM operation_logs WHERE result = 'FAILURE'", String.class));
    CsrfToken token = (CsrfToken) wrongRole.getRequest().getAttribute(CsrfToken.class.getName());
    assertFalse(reasons.contains(token.getToken()));
    assertFalse(reasons.contains(PASSWORD));
    assertFalse(reasons.contains(HASH));
  }

  @Test
  void invalidFieldsLeaveDataUnchangedAndPreserveSubmittedValues() throws Exception {
    String longName = "长".repeat(201);
    MvcResult invalidName = mvc.perform(write(organizer, "/organizer/activities")
            .param("name", longName).param("description", "保留这份简介"))
        .andExpect(status().isBadRequest()).andReturn();
    assertTrue(html(invalidName).contains(longName));
    assertTrue(html(invalidName).contains("保留这份简介"));
    assertEquals(0, count("SELECT COUNT(*) FROM activities"));
    long id = createActivity("日期校验活动");
    String[][] invalid = {
        {"2026-11-15T14:00", "2026-11-15T14:00", "2026-11-14T18:00", "8"},
        {"2026-11-15T14:00", "2026-11-15T16:00", "2026-11-15T14:01", "8"},
        {"2026-02-30T14:00", "2026-11-15T16:00", "2026-11-14T18:00", "8"},
        {"2026-11-15T14:00", "2026-11-15T16:00", "2026-11-14T18:00", "-1"},
        {"2026-11-15T14:00", "2026-11-15T16:00", "2026-11-14T18:00", "many"},
        {"2026-11-15T14:00", "2026-11-15T16:00", "2026-11-14T18:00", "2147483648"}
    };
    for (String[] input : invalid) {
      MvcResult result = mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
              input[0], input[1], input[2], input[3], "保留输入的位置"))
          .andExpect(status().isBadRequest()).andReturn();
      assertTrue(html(result).contains("保留输入的位置"));
      assertTrue(html(result).contains(input[3]));
    }
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
            "2026-11-15T14:00", "2026-11-15T16:00", "2026-11-14T18:00", "8", "地".repeat(201)))
        .andExpect(status().isBadRequest());
    assertEquals(0, count("SELECT COUNT(*) FROM sessions"));
    assertEquals(7, logs("CREATE_SESSION", "FAILURE"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'FAILURE'"
        + " AND (failure_reason IS NULL OR failure_reason = '')"));
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
            "2026-11-15T14:00", "2026-11-15T16:00", "2026-11-15T14:00", "0", "合法边界"))
        .andExpect(status().is3xxRedirection());
    assertState(id, "DRAFT", 1);
  }

  @Test
  void zeroSessionSubmissionAndUnknownReviewDecisionAreRejected() throws Exception {
    long id = createActivity("等待场次的草稿");
    mvc.perform(write(organizer, activityPath(id) + "/submit"))
        .andExpect(status().isBadRequest());
    assertState(id, "DRAFT", 0);
    assertEquals(0, logs("SUBMIT_REVIEW", "SUCCESS"));
    assertEquals(1, logs("SUBMIT_REVIEW", "FAILURE"));
    addSession(id, "2026-11-15");
    submit(id);
    mvc.perform(write(admin, "/admin/reviews/" + id).param("decision", "anything"))
        .andExpect(status().isBadRequest());
    assertState(id, "PENDING", 1);
  }

  @Test
  void pendingAndPublishedActivitiesRejectStaleWriteRequests() throws Exception {
    long id = createActivity("锁定审核内容");
    long sessionId = addSession(id, "2026-11-15");
    submit(id);
    mvc.perform(write(organizer, activityPath(id)).param("name", "审核中偷偷修改")
            .param("description", "应保留原内容"))
        .andExpect(status().isConflict());
    mvc.perform(write(organizer, activityPath(id) + "/submit"))
        .andExpect(status().isConflict());
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions/" + sessionId,
            "2026-11-16T14:00", "2026-11-16T16:00", "2026-11-15T18:00", "9", "审核中修改"))
        .andExpect(status().isConflict());
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
            "2026-11-16T14:00", "2026-11-16T16:00", "2026-11-15T18:00", "9", "审核中新增"))
        .andExpect(status().isConflict());
    assertState(id, "PENDING", 1);
    review(id, "approve");
    mvc.perform(write(organizer, activityPath(id)).param("name", "发布后绕过审核")
            .param("description", "不能直接覆盖"))
        .andExpect(status().isConflict());
    mvc.perform(write(admin, "/admin/reviews/" + id).param("decision", "reject"))
        .andExpect(status().isConflict());
    assertState(id, "PUBLISHED", 1);
    assertEquals("锁定审核内容", jdbc.queryForObject(
        "SELECT name FROM activities WHERE id = ?", String.class, id));
    assertEquals(1, logs("APPROVE_REVIEW", "SUCCESS"));
    assertEquals(1, logs("PUBLISH_ACTIVITY", "SUCCESS"));
    assertEquals(1, logs("REJECT_REVIEW", "FAILURE"));
  }

  @Test
  void oldReviewPageCannotDecideAResubmittedApplication() throws Exception {
    long id = createActivity("第一轮审核内容");
    addSession(id, "2026-11-15");
    submit(id);
    MockHttpSession secondAdmin = login("lu_xi", "ADMIN");
    var versionField = Pattern.compile("name=\"reviewVersion\" value=\"(\\d+)\"")
        .matcher(body(secondAdmin, "/admin/reviews/" + id));
    assertTrue(versionField.find());
    String oldVersion = versionField.group(1);
    review(id, "reject");
    mvc.perform(write(organizer, activityPath(id)).param("name", "第二轮审核内容")
            .param("description", "修改后的内容需要重新审核"))
        .andExpect(status().is3xxRedirection());
    submit(id);
    assertEquals(Long.parseLong(oldVersion) + 1, reviewVersion(id));
    for (String decision : List.of("approve", "reject")) {
      MvcResult result = mvc.perform(write(secondAdmin, "/admin/reviews/" + id)
              .param("decision", decision).param("reviewVersion", oldVersion))
          .andExpect(status().isConflict()).andReturn();
      assertTrue(html(result).contains("审核申请已更新"));
      assertTrue(html(result).contains("第二轮审核内容"));
      assertState(id, "PENDING", 1);
    }
    mvc.perform(write(secondAdmin, "/admin/reviews/" + id).param("decision", "approve"))
        .andExpect(status().isConflict());
    assertEquals(0, logs("PUBLISH_ACTIVITY", "SUCCESS"));
    assertEquals(2, logs("APPROVE_REVIEW", "FAILURE"));
    assertEquals(1, logs("REJECT_REVIEW", "FAILURE"));
    assertFalse(body(participant, "/workshops").contains("第二轮审核内容"));
    review(id, "approve");
    assertState(id, "PUBLISHED", 1);
    assertTrue(body(participant, "/workshops").contains("第二轮审核内容"));
  }

  @Test
  void databaseDateRangeAndSecondPrecisionAreValidatedBeforeSaving() throws Exception {
    long id = createActivity("时间精度校验");
    String[][] invalid = {
        {"0999-12-31T14:00", "1000-01-01T16:00", "0999-12-31T14:00"},
        {"9999-12-31T14:00", "+10000-01-01T16:00", "9999-12-31T14:00"},
        {"2026-11-15T14:00:00.1", "2026-11-15T14:00:00.9", "2026-11-15T14:00:00"}
    };
    for (String[] input : invalid) {
      mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
              input[0], input[1], input[2], "8", "精度实验室"))
          .andExpect(status().isBadRequest());
    }
    assertEquals(0, count("SELECT COUNT(*) FROM sessions"));
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions",
            "2026-11-15T14:00:00.9", "2026-11-15T14:00:01.1", "2026-11-15T14:00:00.1",
            "8", "精度实验室"))
        .andExpect(status().is3xxRedirection());
    assertEquals(LocalDateTime.of(2026, 11, 15, 14, 0), jdbc.queryForObject(
        "SELECT start_time FROM sessions WHERE activity_id = ?", LocalDateTime.class, id));
    assertEquals(LocalDateTime.of(2026, 11, 15, 14, 0, 1), jdbc.queryForObject(
        "SELECT end_time FROM sessions WHERE activity_id = ?", LocalDateTime.class, id));
  }

  @Test
  void draftSessionCannotBeEditedAtItsEndTime() throws Exception {
    long id = createActivity("结束边界校验");
    long sessionId = addSession(id, "2026-11-15");
    clock.setBeijing(LocalDateTime.of(2026, 11, 15, 16, 0));
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions/" + sessionId,
            "2026-11-16T14:00", "2026-11-16T16:00", "2026-11-16T14:00", "12", "修改地点"))
        .andExpect(status().isConflict());
    assertEquals(8, jdbc.queryForObject(
        "SELECT capacity FROM sessions WHERE id = ?", Integer.class, sessionId));
    assertEquals("仙林校区 · 逸夫楼 A203", jdbc.queryForObject(
        "SELECT location FROM sessions WHERE id = ?", String.class, sessionId));
    assertEquals(1, logs("UPDATE_SESSION", "FAILURE"));
    assertEquals(0, logs("UPDATE_SESSION", "SUCCESS"));
  }

  @Test
  void publicationLogFailureRollsBackBusinessAndSuccessLogs() throws Exception {
    long id = createActivity("事务回滚校验");
    addSession(id, "2026-11-15");
    addSession(id, "2026-11-16");
    submit(id);
    jdbc.execute("ALTER TABLE operation_logs ADD CONSTRAINT test_reject_publication"
        + " CHECK (action <> 'PUBLISH_ACTIVITY')");
    try {
      assertThrows(RuntimeException.class,
          () -> management.review(4, id, "approve", reviewVersion(id)));
    } finally {
      jdbc.execute("ALTER TABLE operation_logs DROP CHECK test_reject_publication");
    }
    assertState(id, "PENDING", 2);
    assertEquals(0, logs("APPROVE_REVIEW", "SUCCESS"));
    assertEquals(0, logs("PUBLISH_ACTIVITY", "SUCCESS"));
    assertEquals(1, logs("APPROVE_REVIEW", "FAILURE"));
    assertFalse(body(participant, "/workshops").contains("事务回滚校验"));
    review(id, "approve");
    assertState(id, "PUBLISHED", 2);
  }

  @Test
  void concurrentOppositeReviewsHaveOnlyOneSuccessfulDecision() throws Exception {
    long id = createActivity("并发审核场景");
    addSession(id, "2026-11-15");
    submit(id);
    MockHttpSession secondAdmin = login("lu_xi", "ADMIN");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var approval = executor.submit(() -> concurrentReview(admin, id, "approve", ready, start));
      var rejection = executor.submit(
          () -> concurrentReview(secondAdmin, id, "reject", ready, start));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      int approved = approval.get(10, TimeUnit.SECONDS);
      int rejected = rejection.get(10, TimeUnit.SECONDS);
      boolean approvalWon = approved / 100 == 3;
      boolean rejectionWon = rejected / 100 == 3;
      assertTrue((approvalWon && rejected == 409) || (approved == 409 && rejectionWon),
          "并发审核应只有一个成功：" + approved + ", " + rejected);
      assertState(id, approvalWon ? "PUBLISHED" : "REJECTED", 1);
      assertEquals(1, logs("APPROVE_REVIEW", "SUCCESS") + logs("REJECT_REVIEW", "SUCCESS"));
      assertEquals(1, logs("APPROVE_REVIEW", "FAILURE") + logs("REJECT_REVIEW", "FAILURE"));
      assertEquals(approvalWon ? 1 : 0, logs("PUBLISH_ACTIVITY", "SUCCESS"));
    } finally {
      start.countDown();
    }
  }

  private int concurrentReview(MockHttpSession reviewer, long id, String decision,
      CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    assertTrue(start.await(5, TimeUnit.SECONDS));
    return mvc.perform(write(reviewer, "/admin/reviews/" + id).param("decision", decision)
            .param("reviewVersion", Long.toString(reviewVersion(id))))
        .andReturn().getResponse().getStatus();
  }

  private long createActivity(String name) throws Exception {
    MvcResult result = mvc.perform(write(organizer, "/organizer/activities")
            .param("name", name).param("description", "一起观察材料并动手制作"))
        .andExpect(status().is3xxRedirection()).andReturn();
    String target = result.getResponse().getRedirectedUrl();
    assertTrue(target != null && target.matches("/organizer/activities/\\d+"));
    return Long.parseLong(target.substring(target.lastIndexOf('/') + 1));
  }

  private long addSession(long id, String day) throws Exception {
    mvc.perform(sessionForm(organizer, activityPath(id) + "/sessions", day + "T14:00",
            day + "T16:00", day + "T14:00", "8", "仙林校区 · 逸夫楼 A203"))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(activityPath(id)));
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM sessions WHERE activity_id = ?", Long.class, id);
  }

  private void submit(long id) throws Exception {
    mvc.perform(write(organizer, activityPath(id) + "/submit"))
        .andExpect(status().is3xxRedirection());
  }

  private void review(long id, String decision) throws Exception {
    mvc.perform(write(admin, "/admin/reviews/" + id).param("decision", decision)
            .param("reviewVersion", Long.toString(reviewVersion(id))))
        .andExpect(status().is3xxRedirection());
  }

  private long reviewVersion(long id) {
    return jdbc.queryForObject(
        "SELECT review_version FROM activities WHERE id = ?", Long.class, id);
  }

  private MockHttpServletRequestBuilder write(MockHttpSession user, String path) {
    return post(path).session(user).with(csrf());
  }

  private MockHttpServletRequestBuilder sessionForm(MockHttpSession user, String path,
      String start, String end, String deadline, String capacity, String location) {
    return write(user, path).param("startTime", start).param("endTime", end)
        .param("deadline", deadline).param("capacity", capacity).param("location", location);
  }

  private void assertState(long id, String expected, int sessions) {
    assertEquals(expected, state("activities", id));
    List<String> states = jdbc.queryForList(
        "SELECT publication_status FROM sessions WHERE activity_id = ?", String.class, id);
    assertEquals(sessions, states.size());
    assertTrue(states.stream().allMatch(expected::equals), states.toString());
  }

  private String state(String table, long id) {
    return jdbc.queryForObject("SELECT publication_status FROM " + table + " WHERE id = ?",
        String.class, id);
  }

  private int logs(String action, String result) {
    return count("SELECT COUNT(*) FROM operation_logs WHERE action = ? AND result = ?",
        action, result);
  }

  private int count(String sql, Object... values) {
    return jdbc.queryForObject(sql, Integer.class, values);
  }

  private String activityPath(long id) {
    return "/organizer/activities/" + id;
  }

  private String html(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
