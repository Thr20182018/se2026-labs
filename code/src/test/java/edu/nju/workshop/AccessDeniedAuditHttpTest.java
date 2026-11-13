package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;

class AccessDeniedAuditHttpTest extends WorkshopTestBase {

  @Test
  void plainAndEncodedRoutesAuditRoleAndCsrfRejectionsWithoutBusinessChanges() throws Exception {
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
        {"/admin/reviews/13", "REJECT_REVIEW", "13", null, "reject"}
    };
    int count = 0;
    for (String[] route : routes) {
      for (boolean encoded : new boolean[] {false, true}) {
        String path = encoded ? route[0].replace("activities", "%61ctivities")
            .replace("12", "%31%32").replace("13", "%31%33").replace("34", "%33%34")
            : route[0];
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
  }
}
