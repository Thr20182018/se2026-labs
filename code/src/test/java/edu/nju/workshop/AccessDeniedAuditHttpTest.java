package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;

class AccessDeniedAuditHttpTest extends WorkshopTestBase {

  private static final String[][] IDS = {
      {"12", "13", "34"}, {"%31%32", "%31%33", "%33%34"},
      {"+12", "+13", "+34"}, {"%2B12", "%2B13", "%2B34"}, {"0xC", "0xD", "0x22"}
  };

  @Test
  void equivalentLongIdsAuditRejectionsAndReachAuthorizedBusinessOperations() throws Exception {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "he_qm", "何启明", "ADMIN");
    var participant = login("lin_zy", "PARTICIPANT");
    var organizer = login("su_qing", "ORGANIZER");
    var admin = login("he_qm", "ADMIN");
    activity(12, 2, "安全日志草稿", "DRAFT", null, null);
    activity(13, 2, "安全日志待审核", "PENDING", null, null);
    session(34, 12, "2026-11-20 14:00:00", "2026-11-20 16:00:00",
        "2026-11-19 18:00:00", 8, "DRAFT");
    session(35, 13, "2026-11-20 14:00:00", "2026-11-20 16:00:00",
        "2026-11-19 18:00:00", 8, "PENDING");
    var activities = jdbc.queryForList("SELECT * FROM activities ORDER BY id");
    var sessions = jdbc.queryForList("SELECT * FROM sessions ORDER BY id");
    String[][] routes = {
        {"/organizer/activities", "CREATE_ACTIVITY", null, null, ""},
        {"/organizer/activities/12", "UPDATE_ACTIVITY", "12", null, ""},
        {"/organizer/activities/12/submit", "SUBMIT_REVIEW", "12", null, ""},
        {"/organizer/activities/12/sessions", "CREATE_SESSION", "12", null, ""},
        {"/organizer/activities/12/sessions/34", "UPDATE_SESSION", "12", "34", ""},
        {"/admin/reviews/13", "APPROVE_REVIEW", "13", null, "approve"},
        {"/admin/reviews/13", "REJECT_REVIEW", "13", null, "reject"},
        {"/organizer/activities/not-a-number", "UPDATE_ACTIVITY", null, null, ""}
    };
    int count = 0;
    for (String[] route : routes) {
      for (String[] ids : IDS) {
        String path = route[0].replace("12", ids[0]).replace("13", ids[1]).replace("34", ids[2]);
        if (ids[0].startsWith("%")) {
          path = path.replace("activities", "%61ctivities");
        }
        boolean review = path.startsWith("/admin/");
        for (boolean missingCsrf : new boolean[] {false, true}) {
          long actorId = missingCsrf ? (review ? 3L : 2L) : 1L;
          var actor = missingCsrf ? (review ? admin : organizer) : participant;
          var request = post(URI.create(path)).session(actor).param("decision", route[4])
              .param("name", "不应保存的内容").param("description", "不应保存的简介")
              .param("startTime", "2026-11-21T14:00").param("endTime", "2026-11-21T16:00")
              .param("deadline", "2026-11-20T18:00").param("capacity", "9")
              .param("location", "不应保存的地点");
          if (!missingCsrf) {
            request.with(csrf());
          }
          mvc.perform(request).andExpect(status().isForbidden());
          assertEquals(++count, jdbc.queryForObject(
              "SELECT COUNT(*) FROM operation_logs", Integer.class));
          var log = jdbc.queryForMap("SELECT * FROM operation_logs ORDER BY id DESC LIMIT 1");
          assertEquals(actorId, log.get("actor_id"));
          assertEquals("USER", log.get("actor_type"));
          assertEquals(route[2] == null ? null : Long.valueOf(route[2]), log.get("activity_id"));
          assertEquals(route[3] == null ? null : Long.valueOf(route[3]), log.get("session_id"));
          assertEquals(route[1], log.get("action"));
          assertEquals("FAILURE", log.get("result"));
          assertEquals(missingCsrf ? "安全令牌无效，请刷新页面后重试" : "当前角色无权执行该操作",
              log.get("failure_reason"));
          assertEquals(activities, jdbc.queryForList("SELECT * FROM activities ORDER BY id"));
          assertEquals(sessions, jdbc.queryForList("SELECT * FROM sessions ORDER BY id"));
        }
      }
    }
    for (String[] ids : IDS) {
      String name = "合法编号 " + ids[0];
      mvc.perform(post(URI.create("/organizer/activities/" + ids[0])).session(organizer)
              .with(csrf()).param("name", name).param("description", "应保存到编号 12"))
          .andExpect(status().is3xxRedirection());
      assertEquals(name, jdbc.queryForObject(
          "SELECT name FROM activities WHERE id = 12", String.class));
      var log = jdbc.queryForMap("SELECT * FROM operation_logs ORDER BY id DESC LIMIT 1");
      assertEquals(12L, log.get("activity_id"));
      assertEquals("UPDATE_ACTIVITY", log.get("action"));
      assertEquals("SUCCESS", log.get("result"));
    }
  }
}
