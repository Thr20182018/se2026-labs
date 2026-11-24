package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivityLifecycleHttpTest extends LifecycleTestBase {

  @Test
  void wholeCancellationKeepsEndedHistoryAndCancelsEveryUnfinishedSession() throws Exception {
    jdbc.update("UPDATE sessions SET capacity = 2 WHERE id = 1");
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    registration(30, 5, 1, "CHECKED_IN");
    registration(31, 7, 1, "WAITLIST_EXPIRED");
    registration(32, 8, 1, "CANCELLED");
    session(2, 1, "2026-11-10 08:00:00", "2026-11-10 10:00:00",
        "2026-11-09 18:00:00", 3, "PUBLISHED");
    registration(40, 1, 2, "REGISTERED");
    registration(50, 4, 2, "CHECKED_IN");
    registration(60, 5, 2, "ABSENT");
    jdbc.update("UPDATE registrations SET checked_in_at = '2026-11-10 09:00:00' WHERE id = 50");
    futureSession(3, 1, 1);
    jdbc.update("UPDATE sessions SET publication_status = 'PENDING' WHERE id = 3");
    jdbc.update("UPDATE activities SET pending_name = '待审蓝晒', pending_description = '待审简介',"
        + " revision_pending = 1 WHERE id = 1");
    final var endedBefore = jdbc.queryForList("SELECT * FROM registrations WHERE session_id = 2"
        + " ORDER BY id");

    submit(write(2, ACTIVITY + "/cancel"), 200);
    assertEquals("CANCELLED", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals("CANCELLED", publication("sessions", 3));
    assertEquals("PUBLISHED", publication("sessions", 2));
    assertEquals(List.of("CANCELLED", "CANCELLED", "CANCELLED", "WAITLIST_EXPIRED", "CANCELLED"),
        jdbc.queryForList("SELECT status FROM registrations WHERE session_id = 1 ORDER BY id",
            String.class));
    assertEquals(endedBefore, jdbc.queryForList("SELECT * FROM registrations WHERE session_id = 2"
        + " ORDER BY id"));
    assertEquals(0, occupied(1));
    assertEquals(3, occupied(2));
    assertNull(jdbc.queryForObject("SELECT pending_name FROM activities WHERE id = 1",
        String.class));
    assertNull(jdbc.queryForObject("SELECT pending_description FROM activities WHERE id = 1",
        String.class));
    assertEquals(0, count("SELECT revision_pending FROM activities WHERE id = 1"));
    assertEquals(0, logs("PROMOTE_WAITLIST", "SUCCESS"));
    assertEquals(1, logs("CANCEL_ACTIVITY", "SUCCESS"));
    assertTransition(30, "CANCEL_REGISTRATION", 2L, "CHECKED_IN", "CANCELLED");
    String historical = body(actor(1), "/workshops?selected=2");
    assertTrue(historical.contains("蓝晒工作坊"));
    assertTrue(historical.contains("session-2"));
    assertTrue(body(actor(2), ACTIVITY).contains("已取消"));
    clock.setBeijing(END.plusDays(1));
    submit(write(2, ACTIVITY + "/cancel"), 200);
    assertEquals(1, logs("CANCEL_ACTIVITY", "SUCCESS"));
    assertEquals(endedBefore, jdbc.queryForList("SELECT * FROM registrations WHERE session_id = 2"
        + " ORDER BY id"));
  }

  @ParameterizedTest
  @CsvSource({"2, organizer", "3, admin"})
  void oneSessionCancellationKeepsTheActivityAndOtherSessionsPublished(long actorId, String area)
      throws Exception {
    futureSession(2, 1, 1);
    registration(10, 1, 1, "CHECKED_IN");
    registration(20, 4, 2, "REGISTERED");
    String target = "/" + area + "/activities/1/sessions/1/cancel";
    submit(write(actorId, target), 200);
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals("PUBLISHED", publication("sessions", 2));
    assertEquals("CANCELLED", state(10));
    assertEquals("REGISTERED", state(20));
    assertEquals(0, occupied(1));
    apply(5, 1, "register", 409);
    submit(write(2, checkInPath(1, 10)), 409);
    submit(write(2, SCENE + "/capacity").param("capacity", "2"), 409);
    submit(write(actorId, target), 200);
    assertEquals(1, logs("CANCEL_SESSION", "SUCCESS"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'CANCEL_SESSION'"
        + " AND result = 'SUCCESS' AND actor_id = ?", actorId));
    assertEquals(0, logs("PROMOTE_WAITLIST", "SUCCESS"));
    assertTrue(body(actor(1), "/workshops?selected=1").contains("已取消"));
    assertTrue(body(actor(2), SCENE + "/attendance").contains("已取消"));
    assertFalse(body(actor(2), ACTIVITY).contains(SCENE + "/capacity"));
  }

  @Test
  void exactEndAndAnActivityWithoutSessionsCannotBeCancelled() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    clock.setBeijing(END);
    submit(write(2, ACTIVITY + "/cancel"), 409);
    submit(write(3, "/admin/activities/1/sessions/1/cancel"), 409);
    activity(2, 2, "没有场次的草稿", "DRAFT", null, null);
    submit(write(2, "/organizer/activities/2/cancel"), 409);
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("PUBLISHED", publication("sessions", 1));
    assertEquals("DRAFT", publication("activities", 2));
    assertEquals("REGISTERED", state(10));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
  }

  @Test
  void administratorManagementPagesCoverBothOrganizersAndEnforceTheirRole() throws Exception {
    activity(2, 6, "植物拓印工作坊", "PUBLISHED", null, null);
    futureSession(2, 2, 2);
    mvc.perform(get("/admin/activities").session(actor(3)))
        .andExpect(status().isOk()).andExpect(view().name("admin-activities"))
        .andExpect(model().attributeExists("activities"));
    String listing = body(actor(3), "/admin/activities");
    assertTrue(listing.contains("蓝晒工作坊"));
    assertTrue(listing.contains("植物拓印工作坊"));
    mvc.perform(get("/admin/activities/2").session(actor(3)))
        .andExpect(status().isOk()).andExpect(view().name("admin-activity"))
        .andExpect(model().attributeExists("activity"));
    for (long actorId : List.of(1L, 2L)) {
      mvc.perform(get("/admin/activities").session(actor(actorId)))
          .andExpect(status().isForbidden());
      mvc.perform(get("/admin/activities/2").session(actor(actorId)))
          .andExpect(status().isForbidden());
    }
    submit(write(3, "/admin/activities/2/cancel"), 200);
    assertEquals("CANCELLED", publication("activities", 2));
    assertEquals("PUBLISHED", publication("activities", 1));
  }

  @Test
  void nonOwnersWrongRolesAndMissingCsrfCannotCancel() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    for (String target : List.of(ACTIVITY + "/cancel", SCENE + "/cancel")) {
      for (long actorId : List.of(1L, 6L)) {
        String rejected = mvc.perform(write(actorId, target)).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        assertFalse(rejected.contains("蓝晒工作坊"));
      }
      mvc.perform(post(target).session(actor(2))).andExpect(status().isForbidden());
    }
    mvc.perform(write(2, "/admin/activities/1/cancel")).andExpect(status().isForbidden());
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("PUBLISHED", publication("sessions", 1));
    assertEquals("REGISTERED", state(10));
    assertEquals(4, logs("CANCEL_ACTIVITY", "FAILURE"));
    assertEquals(3, logs("CANCEL_SESSION", "FAILURE"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"activity", "session"})
  void cancellationAuditFailureRestoresEveryAffectedRecordAndAllowsRetry(String scope)
      throws Exception {
    futureSession(2, 1, 1);
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    registration(30, 5, 2, "CHECKED_IN");
    jdbc.update("UPDATE activities SET pending_name = '待审蓝晒', pending_description = '待审简介',"
        + " revision_pending = 1 WHERE id = 1");
    boolean whole = scope.equals("activity");
    String target = (whole ? ACTIVITY : SCENE) + "/cancel";
    String action = whole ? "CANCEL_ACTIVITY" : "CANCEL_SESSION";
    var request = write(2, target);
    rejectSuccessLog(action);
    try {
      assertThrows(ServletException.class, () -> mvc.perform(request));
    } finally {
      restoreSuccessLog();
    }
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals(List.of("PUBLISHED", "PUBLISHED"), jdbc.queryForList(
        "SELECT publication_status FROM sessions ORDER BY id", String.class));
    assertEquals(List.of("REGISTERED", "WAITLIST", "CHECKED_IN"), jdbc.queryForList(
        "SELECT status FROM registrations ORDER BY id", String.class));
    assertEquals("待审蓝晒", jdbc.queryForObject("SELECT pending_name FROM activities WHERE id = 1",
        String.class));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
    assertEquals(1, logs(action, "FAILURE"));
    submit(write(2, target), 200);
    assertEquals("CANCELLED", state(10));
    assertEquals("CANCELLED", state(20));
    assertEquals(whole ? "CANCELLED" : "CHECKED_IN", state(30));
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRAFT", "PENDING"})
  void cancellingOneUnpublishedSessionDoesNotBlockPublishingTheRemainingSession(String state)
      throws Exception {
    futureSession(2, 1, 1);
    jdbc.update("UPDATE activities SET publication_status = ?, review_version = ? WHERE id = 1",
        state, state.equals("PENDING") ? 1 : 0);
    jdbc.update("UPDATE sessions SET publication_status = ?", state);
    submit(write(2, SCENE + "/cancel"), 200);
    if (state.equals("DRAFT")) {
      mvc.perform(write(2, ACTIVITY + "/submit")).andExpect(status().is3xxRedirection());
    }
    assertEquals("PENDING", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals("PENDING", publication("sessions", 2));
    review("approve", 302);
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals("PUBLISHED", publication("sessions", 2));
    apply(1, 1, "register", 409);
    apply(1, 2, "register", 200);
    String publicPage = body(actor(1), "/workshops");
    assertFalse(publicPage.contains("id=\"session-1\""));
    assertTrue(publicPage.contains("id=\"session-2\""));
  }

  @Test
  void cancellingUnreviewedContentsNeverMakesThemPublic() throws Exception {
    futureSession(2, 1, 1);
    futureSession(3, 1, 1);
    jdbc.update("UPDATE sessions SET publication_status = 'DRAFT' WHERE id = 2");
    jdbc.update("UPDATE sessions SET publication_status = 'PENDING' WHERE id = 3");
    submit(write(2, ACTIVITY + "/sessions/2/cancel"), 200);
    submit(write(3, "/admin/activities/1/sessions/3/cancel"), 200);
    String publishedParent = body(actor(1), "/workshops?selected=2");
    assertTrue(publishedParent.contains("id=\"session-1\""));
    assertFalse(publishedParent.contains("id=\"session-2\""));
    assertFalse(publishedParent.contains("id=\"session-3\""));
    submit(write(2, ACTIVITY + "/cancel"), 200);
    activity(2, 2, "未审核的植物印染", "DRAFT", null, null);
    futureSession(4, 2, 1);
    jdbc.update("UPDATE sessions SET publication_status = 'DRAFT' WHERE id = 4");
    submit(write(2, "/organizer/activities/2/cancel"), 200);
    String cancelledParents = body(actor(1), "/workshops");
    assertTrue(cancelledParents.contains("id=\"session-1\""));
    assertTrue(cancelledParents.contains("已取消"));
    assertFalse(cancelledParents.contains("id=\"session-2\""));
    assertFalse(cancelledParents.contains("id=\"session-3\""));
    assertFalse(cancelledParents.contains("未审核的植物印染"));
  }

  @Test
  void cancellingAllPendingSessionsAllowsRejectionAndNewSessionResubmission()
      throws Exception {
    jdbc.update("UPDATE activities SET publication_status = 'PENDING', review_version = 1"
        + " WHERE id = 1");
    jdbc.update("UPDATE sessions SET publication_status = 'PENDING' WHERE id = 1");
    submit(write(2, SCENE + "/cancel"), 200);
    review("approve", 409);
    assertEquals("PENDING", publication("activities", 1));
    review("reject", 302);
    assertEquals("REJECTED", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    mvc.perform(write(2, ACTIVITY + "/sessions")
            .param("startTime", "2026-11-12T14:00")
            .param("endTime", "2026-11-12T16:00")
            .param("deadline", "2026-11-11T18:00")
            .param("capacity", "2").param("location", "仙林校区 · 拓印教室"))
        .andExpect(status().is3xxRedirection());
    long replacement = jdbc.queryForObject("SELECT MAX(id) FROM sessions", Long.class);
    assertEquals("DRAFT", publication("sessions", replacement));
    mvc.perform(write(2, ACTIVITY + "/submit")).andExpect(status().is3xxRedirection());
    review("approve", 302);
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals("PUBLISHED", publication("sessions", replacement));
    apply(1, replacement, "register", 200);
  }

  private void review(String decision, int expected) throws Exception {
    long version = jdbc.queryForObject("SELECT review_version FROM activities WHERE id = 1",
        Long.class);
    mvc.perform(write(3, "/admin/reviews/1").param("decision", decision)
            .param("reviewVersion", Long.toString(version)))
        .andExpect(status().is(expected));
  }
}
