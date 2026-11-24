package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.nju.workshop.attendance.AttendanceService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class ManagementQueryHttpTest extends LifecycleTestBase {

  @Autowired
  AttendanceService attendance;

  @Test
  void statisticsCountAllStatesAndEmptySessionsWithinTheSelectedScope() throws Exception {
    inventory();
    assertSummary(2, "", 3, 1, 1, 9);
    assertSummary(3, "", 4, 1, 1, 12);
    assertSummary(3, "?activityId=1&sessionId=1", 3, 1, 1, 7);
    assertSummary(2, "?sessionId=3", 0, 0, 0, 2);
    JsonNode own = pageData(2, "/organizer/statistics");
    assertEquals(6, own.path("total").asInt());
    assertEquals(7, pageData(3, "/admin/statistics").path("total").asInt());
    assertFalse(body(actor(2), "/organizer/statistics").contains("其他组织者的活动"));
    assertTrue(body(actor(3), "/admin/statistics").contains("其他组织者的活动"));
  }

  @Test
  void statisticsFollowPromotionCheckInAndActivityCancellation() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    assertSummary(2, "", 1, 0, 0, 0);
    submit(write(1, cancelPath(1, 10)), 200);
    assertEquals("REGISTERED", state(20));
    assertSummary(2, "", 1, 0, 0, 0);
    submit(write(2, checkInPath(1, 20)), 200);
    assertSummary(2, "", 1, 1, 0, 0);
    submit(write(3, "/admin/activities/1/cancel"), 200);
    assertSummary(2, "", 0, 0, 0, 1);
    String csv = csv(2, "registrations", "");
    assertTrue(csv.contains("已取消"));
    assertTrue(csv.contains("2026-11-10 10:00:00"));
  }

  @Test
  void absenceDoesNotReduceRegisteredCount() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    clock.setBeijing(END);
    attendance.markEndedAbsent();
    assertSummary(2, "", 1, 0, 1, 0);
  }

  @Test
  void exportsIncludeEveryPageAndStatisticsUseFullScope() throws Exception {
    jdbc.update("UPDATE sessions SET capacity = 30 WHERE id = 1");
    for (int i = 10; i < 35; i++) {
      user(i, "export_" + i, "导出参与者" + i, "PARTICIPANT");
      registration(i, i, 1, "REGISTERED");
    }
    JsonNode first = pageData(2, "/organizer/statistics");
    JsonNode second = pageData(2, "/organizer/statistics?page=2");
    assertEquals(25, first.path("total").asInt());
    assertEquals(20, first.path("rows").size());
    assertEquals(5, second.path("rows").size());
    assertEquals(2, pageData(2, "/organizer/statistics?page=999").path("page").asInt());
    assertSummary(2, "?page=2", 25, 0, 0, 5);
    String csv = csv(2, "registrations", "?sessionId=1");
    for (int i = 10; i < 35; i++) {
      assertTrue(csv.contains("export_" + i));
    }
    assertEquals(26, csv.lines().count());
    assertTrue(csv(2, "summary", "?sessionId=1").contains("25"));
  }

  @Test
  void csvPreservesChineseAndQuotedMultilineValuesAndNeutralizesFormulas() throws Exception {
    String title = "蓝晒,\"植物\"\n工作坊";
    jdbc.update("UPDATE activities SET name = ? WHERE id = 1", title);
    jdbc.update("UPDATE users SET display_name = '=1+1' WHERE id = 1");
    registration(10, 1, 1, "REGISTERED");
    String csv = csv(2, "registrations", "");
    assertTrue(csv.startsWith("\uFEFF"));
    assertTrue(csv.contains("\"蓝晒,\"\"植物\"\"\n工作坊\""));
    assertTrue(csv.contains("'=1+1"));
    assertTrue(csv.contains("\r\n"));
    assertTrue(csv.contains("报名成功"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"statistics", "statistics/registrations.csv", "statistics/summary.csv",
      "logs"})
  void managementQueriesEnforceRoleAndObjectScope(String resource) throws Exception {
    inventory();
    mvc.perform(get("/admin/" + resource).session(actor(2))).andExpect(status().isForbidden());
    mvc.perform(get("/organizer/" + resource).session(actor(1)))
        .andExpect(status().isForbidden());
    for (String query : List.of("?activityId=2", "?sessionId=2")) {
      mvc.perform(get("/organizer/" + resource + query).session(actor(2)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/admin/" + resource + "?activityId=1&sessionId=2").session(actor(3)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/admin/" + resource + "?sessionId=999").session(actor(3)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyQueriesAndExportsKeepHeadersAndZeroTotals() throws Exception {
    assertSummary(2, "", 0, 0, 0, 1);
    assertEquals(0, pageData(2, "/organizer/statistics").path("total").asInt());
    assertEquals(1, csv(2, "registrations", "").lines().count());
    assertEquals(0, pageData(2, "/organizer/logs").path("total").asInt());
  }

  @Test
  void logScopeIncludesAdministratorAndSystemActionsOnOwnedActivities() throws Exception {
    inventory();
    log(10, null, 1L, 1L, "AUTO_PROMOTE", "SUCCESS", null, null, null);
    log(11, 3L, 1L, null, "APPROVE_ACTIVITY", "SUCCESS", null, null, null);
    log(12, 6L, 2L, 2L, "CREATE_ACTIVITY", "SUCCESS", null, null, null);
    log(13, 3L, 999L, null, "CANCEL_ACTIVITY", "FAILURE", "活动不存在", null, null);
    log(14, 1L, null, 1L, "REGISTER", "FAILURE", "请求被拒绝", null, null);
    JsonNode own = pageData(2, "/organizer/logs");
    assertEquals(3, own.path("total").asInt());
    assertEquals(5, pageData(3, "/admin/logs").path("total").asInt());
    String html = body(actor(2), "/organizer/logs");
    assertTrue(html.contains("系统"));
    assertTrue(html.contains("陆曦"));
    assertFalse(html.contains("其他组织者的活动"));
    mvc.perform(get("/organizer/logs?selected=12").session(actor(2)))
        .andExpect(status().is4xxClientError());
    assertTrue(body(actor(3), "/admin/logs?selected=13").contains("活动不存在"));
  }

  @Test
  void logFiltersUseInclusiveTimesAndStableNewestFirstPagination() throws Exception {
    for (int i = 1; i <= 23; i++) {
      log(i, 2L, 1L, 1L, "UPDATE_CAPACITY", "SUCCESS", null, null, null);
    }
    log(24, null, 1L, null, "PUBLISH_ACTIVITY", "SUCCESS", null, null, null);
    jdbc.update("UPDATE operation_logs SET operated_at = '2026-11-10 09:59:59' WHERE id = 1");
    String filter = "?sessionId=1&action=UPDATE_CAPACITY"
        + "&from=2026-11-10T10:00:00&to=2026-11-10T10:00:00";
    JsonNode first = pageData(2, "/organizer/logs" + filter);
    assertEquals(22, first.path("total").asInt());
    assertEquals(23, first.path("rows").get(0).path("id").asInt());
    assertEquals(4, first.path("rows").get(19).path("id").asInt());
    JsonNode second = pageData(2, "/organizer/logs" + filter + "&page=2");
    assertEquals(2, second.path("rows").size());
    assertEquals(3, second.path("rows").get(0).path("id").asInt());
    String html = body(actor(2), "/organizer/logs" + filter);
    assertTrue(html.contains("action=UPDATE_CAPACITY"));
    assertTrue(html.contains("page=2"));
    mvc.perform(get("/organizer/logs?from=2026-11-11T00:00&to=2026-11-10T00:00")
        .session(actor(2))).andExpect(status().isBadRequest());
  }

  @Test
  void mismatchedLogTargetsDoNotExposeAnotherOrganizersSession() throws Exception {
    inventory();
    log(10, 2L, 1L, 2L, "CANCEL_SESSION", "FAILURE", "无权访问该场次", null, null);
    JsonNode selected = pageData(2, "/organizer/logs?selected=10").path("selected");
    assertTrue(selected.path("sessionStart").isNull());
    assertFalse(body(actor(2), "/organizer/logs?selected=10").contains("其他组织者的活动"));
  }

  @Test
  void logDetailsEscapeSavedContentAndShowFailureReason() throws Exception {
    log(10, 2L, 1L, 1L, "UPDATE_CAPACITY", "SUCCESS", null,
        "{\"name\":\"<script>alert(1)</script>\",\"capacity\":1}", "{\"capacity\":2}");
    log(11, 2L, 1L, 1L, "UPDATE_CAPACITY", "FAILURE", "容量不能小于当前报名人数", null, null);
    String success = body(actor(2), "/organizer/logs?selected=10");
    assertTrue(success.contains("capacity"));
    assertTrue(success.contains("&lt;script&gt;"));
    assertFalse(success.contains("<script>alert(1)</script>"));
    String failure = body(actor(2), "/organizer/logs?selected=11");
    assertTrue(failure.contains("容量不能小于当前报名人数"));
    assertFalse(failure.contains(HASH));
  }

  private void inventory() {
    jdbc.update("UPDATE sessions SET capacity = 10 WHERE id = 1");
    List<String> states = List.of("REGISTERED", "CHECKED_IN", "ABSENT", "WAITLIST",
        "WAITLIST_EXPIRED", "CANCELLED");
    long[] participants = {1, 4, 5, 7, 8, 9};
    for (int i = 0; i < states.size(); i++) {
      registration(101 + i, participants[i], 1, states.get(i));
    }
    jdbc.update("UPDATE registrations SET checked_in_at = '2026-11-10 10:00:00' WHERE id = 102");
    activity(2, 6, "其他组织者的活动", "PUBLISHED", null, null);
    futureSession(2, 2, 4);
    registration(201, 1, 2, "REGISTERED");
    futureSession(3, 1, 2);
  }

  private JsonNode pageData(long actorId, String path) throws Exception {
    MvcResult result = mvc.perform(get(path).session(actor(actorId)))
        .andExpect(status().isOk()).andReturn();
    return json.valueToTree(Objects.requireNonNull(result.getModelAndView()).getModel()
        .get("pageData"));
  }

  private void assertSummary(long actorId, String query, long registered, long attended,
      long absent, long remaining) throws Exception {
    String path = (actorId == 3 ? "/admin" : "/organizer") + "/statistics" + query;
    JsonNode summary = pageData(actorId, path).path("summary");
    assertTrue(summary.has("registered") && summary.has("attended")
        && summary.has("absent") && summary.has("remaining"));
    assertEquals(registered, summary.path("registered").asLong());
    assertEquals(attended, summary.path("attended").asLong());
    assertEquals(absent, summary.path("absent").asLong());
    assertEquals(remaining, summary.path("remaining").asLong());
  }

  private String csv(long actorId, String kind, String query) throws Exception {
    String prefix = actorId == 3 ? "/admin" : "/organizer";
    var response = mvc.perform(get(prefix + "/statistics/" + kind + ".csv" + query)
        .session(actor(actorId))).andExpect(status().isOk()).andReturn().getResponse();
    assertTrue(Objects.requireNonNull(response.getContentType()).startsWith("text/csv"));
    assertTrue(Objects.requireNonNull(response.getHeader("Content-Disposition"))
        .contains("attachment"));
    return response.getContentAsString(StandardCharsets.UTF_8);
  }

  private void log(long id, Long actorId, Long activityId, Long sessionId, String action,
      String result, String reason, String before, String after) {
    jdbc.update("INSERT INTO operation_logs (id, operated_at, actor_type, actor_id, activity_id,"
        + " session_id, action, result, failure_reason, before_value, after_value)"
        + " VALUES (?, '2026-11-10 10:00:00', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id, actorId == null ? "SYSTEM" : "USER", actorId, activityId, sessionId, action, result,
        reason, before, after);
  }
}
