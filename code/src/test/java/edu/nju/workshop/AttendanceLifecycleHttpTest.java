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

import edu.nju.workshop.attendance.AttendanceRow;
import edu.nju.workshop.attendance.AttendanceService;
import jakarta.servlet.ServletException;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceLifecycleHttpTest extends LifecycleTestBase {

  @Autowired
  AttendanceService attendance;

  @Test
  void firstCheckInUsesServerSecondsAndDuplicatePreservesTheFirstTime() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    clock.setBeijing(NOW.plusNanos(987654321));
    submit(write(2, checkInPath(1, 10)), 200);
    assertEquals("CHECKED_IN", state(10));
    assertEquals(NOW, checkedInAt(10));
    assertEquals(1, occupied(1));
    clock.setBeijing(NOW.plusMinutes(5));
    var duplicate = submit(write(2, checkInPath(1, 10)), 200);
    assertTrue(duplicate.path("message").asText().contains("已签到"));
    assertEquals(NOW, checkedInAt(10));
    assertEquals(1, logs("CHECK_IN", "SUCCESS"));
    assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE status = 'CHECKED_IN'"));
    assertTransition(10, "CHECK_IN", 2L, "REGISTERED", "CHECKED_IN");
    String page = body(actor(2), SCENE + "/attendance");
    assertTrue(page.contains("林知远"));
    assertTrue(page.contains("lin_zy"));
    assertTrue(page.contains("已签到"));
    assertTrue(page.contains("2026-11-10 10:00"));
    assertFalse(page.contains(checkInPath(1, 10)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void duplicateCheckInAfterEndReturnsExistingResultEvenWhenParentIsCancelled(boolean cancelParent)
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    submit(write(2, checkInPath(1, 10)), 200);
    clock.setBeijing(END);
    if (cancelParent) {
      session(2, 1, "2026-11-13 14:00:00", "2026-11-13 16:00:00",
          "2026-11-12 18:00:00", 1, "PUBLISHED");
      submit(write(3, "/admin/activities/1/cancel"), 200);
      assertEquals("CANCELLED", publication("activities", 1));
    }
    var duplicate = submit(write(2, checkInPath(1, 10)), 200);
    assertEquals("已签到", duplicate.path("message").asText());
    assertEquals("CHECKED_IN", state(10));
    assertEquals(NOW, checkedInAt(10));
    assertEquals(1, occupied(1));
    assertEquals(1, logs("CHECK_IN", "SUCCESS"));
    assertEquals(0, logs("CHECK_IN", "FAILURE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"WAITLIST", "CANCELLED", "WAITLIST_EXPIRED", "ABSENT"})
  void checkInRejectsEveryOtherRegistrationState(String state) throws Exception {
    registration(10, 1, 1, state);
    submit(write(2, checkInPath(1, 10)), 409);
    assertEquals(state, state(10));
    assertNull(checkedInAt(10));
    assertEquals(0, logs("CHECK_IN", "SUCCESS"));
    assertEquals(1, logs("CHECK_IN", "FAILURE"));
  }

  @Test
  void attendeeListIncludesCancelledHistoryInStableOrderAndOnlyTheOwnerCanUseIt()
      throws Exception {
    registration(30, 1, 1, "REGISTERED");
    registration(10, 1, 1, "CANCELLED");
    queued(20, 4, 1);
    var result = mvc.perform(get(SCENE + "/attendance").session(actor(2)))
        .andExpect(status().isOk()).andExpect(view().name("attendance"))
        .andExpect(model().attributeExists("activity", "workshopSession", "attendees")).andReturn();
    var rows = (List<?>) Objects.requireNonNull(result.getModelAndView()).getModel()
        .get("attendees");
    assertEquals(List.of(10L, 20L, 30L), rows.stream().map(AttendanceRow.class::cast)
        .map(AttendanceRow::id).toList());
    String page = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(page.contains("林知远"));
    assertTrue(page.contains("陈安然"));
    assertTrue(page.contains("lin_zy"));
    assertTrue(page.contains("已取消"));
    assertTrue(page.contains(checkInPath(1, 30)));
    for (long actorId : List.of(1L, 3L, 6L)) {
      var denied = mvc.perform(get(SCENE + "/attendance").session(actor(actorId)))
          .andExpect(status().isForbidden()).andReturn();
      assertFalse(denied.getResponse().getContentAsString().contains("lin_zy"));
      mvc.perform(write(actorId, checkInPath(1, 30))).andExpect(status().isForbidden());
    }
    mvc.perform(post(checkInPath(1, 30)).session(actor(2))).andExpect(status().isForbidden());
    assertEquals("REGISTERED", state(30));
    assertEquals(4, logs("CHECK_IN", "FAILURE"));
    clock.setBeijing(END);
    assertFalse(body(actor(2), SCENE + "/attendance").contains(checkInPath(1, 30)));
    assertTrue(body(actor(2), SCENE + "/attendance").contains("lin_zy"));
  }

  @Test
  void mismatchedRegistrationCannotBeCheckedInThroughAnotherSession() throws Exception {
    futureSession(2, 1, 1);
    registration(10, 1, 1, "REGISTERED");
    submit(write(2, checkInPath(2, 10)), 404);
    submit(write(2, checkInPath(1, 999)), 404);
    activity(2, 6, "其他组织者的活动", "PUBLISHED", null, null);
    futureSession(3, 2, 1);
    registration(20, 4, 3, "REGISTERED");
    submit(write(2, checkInPath(3, 20)), 403);
    submit(write(2, ACTIVITY + "/sessions/3/cancel"), 403);
    assertEquals("REGISTERED", state(20));
    assertEquals("PUBLISHED", publication("sessions", 3));
    assertEquals("REGISTERED", state(10));
    assertNull(checkedInAt(10));
    assertEquals(0, logs("CHECK_IN", "SUCCESS"));
  }

  @Test
  void exactEndMarksOnlyRegisteredAsAbsentEvenWhenTheParentWasCancelled() {
    jdbc.update("UPDATE sessions SET capacity = 3 WHERE id = 1");
    registration(10, 1, 1, "REGISTERED");
    registration(20, 4, 1, "CHECKED_IN");
    queued(30, 5, 1);
    registration(40, 7, 1, "CANCELLED");
    registration(50, 8, 1, "WAITLIST_EXPIRED");
    registration(60, 9, 1, "ABSENT");
    futureSession(2, 1, 1);
    registration(70, 1, 2, "REGISTERED");
    jdbc.update("UPDATE sessions SET publication_status = 'CANCELLED' WHERE id = 2");
    futureSession(3, 1, 1);
    registration(80, 1, 3, "REGISTERED");
    jdbc.update("UPDATE sessions SET end_time = '2026-11-12 16:00:01' WHERE id = 3");
    jdbc.update("UPDATE activities SET publication_status = 'CANCELLED' WHERE id = 1");
    clock.setBeijing(END.minusNanos(1));
    attendance.markEndedAbsent();
    assertEquals("REGISTERED", state(10));
    clock.setBeijing(END);
    attendance.markEndedAbsent();
    attendance.markEndedAbsent();
    assertEquals(List.of("ABSENT", "CHECKED_IN", "WAITLIST", "CANCELLED", "WAITLIST_EXPIRED",
        "ABSENT", "REGISTERED", "REGISTERED"), jdbc.queryForList(
            "SELECT status FROM registrations ORDER BY id", String.class));
    assertEquals(3, occupied(1));
    assertEquals(1, logs("MARK_ABSENT", "SUCCESS"));
    assertTransition(10, "MARK_ABSENT", null, "REGISTERED", "ABSENT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"activity", "session"})
  void concurrentCancellationAndCheckInAlwaysFinishWithCancelledRecords(String scope)
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    String cancel = "/admin/activities/1" + (scope.equals("session") ? "/sessions/1" : "")
        + "/cancel";
    List<Integer> results = race(write(3, cancel), write(2, checkInPath(1, 10)));
    assertEquals(200, results.get(0));
    assertTrue(results.get(1) == 200 || results.get(1) == 409, results.toString());
    assertEquals("CANCELLED", state(10));
    assertEquals("CANCELLED", publication("sessions", 1));
    assertEquals(0, occupied(1));
    assertEquals(results.get(1) == 200 ? 1 : 0, logs("CHECK_IN", "SUCCESS"));
    clock.setBeijing(END);
    attendance.markEndedAbsent();
    assertEquals("CANCELLED", state(10));
    assertEquals(0, logs("MARK_ABSENT", "SUCCESS"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"check-in", "cancel"})
  void executionAtEndAfterWaitingForASessionLockRejectsTheOperation(String operation)
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    clock.setBeijing(END.minusSeconds(1));
    var request = write(2, operation.equals("check-in") ? checkInPath(1, 10) : SCENE + "/cancel");
    CountDownLatch started = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor();
        Connection lock = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
      lock.setAutoCommit(false);
      try (var statement = lock.prepareStatement("SELECT id FROM sessions WHERE id = 1 FOR UPDATE");
          var row = statement.executeQuery()) {
        assertTrue(row.next());
      }
      var result = executor.submit(() -> {
        started.countDown();
        return mvc.perform(request).andReturn().getResponse().getStatus();
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> result.get(150, TimeUnit.MILLISECONDS));
      clock.setBeijing(END);
      lock.commit();
      assertEquals(409, result.get(5, TimeUnit.SECONDS));
    }
    assertEquals("PUBLISHED", publication("sessions", 1));
    assertEquals("REGISTERED", state(10));
    assertNull(checkedInAt(10));
  }

  @Test
  void checkInAuditFailureRollsBackStatusAndTimestamp() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    var request = write(2, checkInPath(1, 10));
    rejectSuccessLog("CHECK_IN");
    try {
      assertThrows(ServletException.class, () -> mvc.perform(request));
    } finally {
      restoreSuccessLog();
    }
    assertEquals("REGISTERED", state(10));
    assertNull(checkedInAt(10));
    assertEquals(0, logs("CHECK_IN", "SUCCESS"));
    assertEquals(1, logs("CHECK_IN", "FAILURE"));
    submit(write(2, checkInPath(1, 10)), 200);
    assertEquals("CHECKED_IN", state(10));
    assertEquals(NOW, checkedInAt(10));
  }

  @Test
  void absenceAuditFailureRollsBackTheWholeSessionAndRetainsSystemFailureIdentity() {
    jdbc.update("UPDATE sessions SET capacity = 2 WHERE id = 1");
    registration(10, 1, 1, "REGISTERED");
    registration(20, 4, 1, "REGISTERED");
    clock.setBeijing(END);
    jdbc.execute("ALTER TABLE operation_logs ADD CONSTRAINT test_reject_lifecycle"
        + " CHECK (action <> 'MARK_ABSENT' OR result <> 'SUCCESS' OR registration_id <> 20)");
    try {
      assertThrows(RuntimeException.class, () -> attendance.markEndedAbsent());
    } finally {
      restoreSuccessLog();
    }
    assertEquals(List.of("REGISTERED", "REGISTERED"), jdbc.queryForList(
        "SELECT status FROM registrations ORDER BY id", String.class));
    assertEquals(0, logs("MARK_ABSENT", "SUCCESS"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'MARK_ABSENT'"
        + " AND result = 'FAILURE' AND actor_type = 'SYSTEM' AND actor_id IS NULL"
        + " AND registration_id IS NOT NULL"));
    attendance.markEndedAbsent();
    assertEquals(2, logs("MARK_ABSENT", "SUCCESS"));
    assertEquals(2, occupied(1));
  }

  private LocalDateTime checkedInAt(long id) {
    return jdbc.queryForObject("SELECT checked_in_at FROM registrations WHERE id = ?",
        LocalDateTime.class, id);
  }
}
