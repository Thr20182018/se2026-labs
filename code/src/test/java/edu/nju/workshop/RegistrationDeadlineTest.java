package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.nju.workshop.registration.RegistrationAdmission;
import edu.nju.workshop.registration.RegistrationService;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class RegistrationDeadlineTest extends WorkshopTestBase {

  @Autowired
  RegistrationService registrations;

  @Autowired
  RegistrationAdmission admission;

  private final LocalDateTime deadline = LocalDateTime.of(2026, 11, 11, 18, 0);

  @BeforeEach
  void prepare() {
    clock.setBeijing(deadline.minusSeconds(1));
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "chen_an", "陈安然", "PARTICIPANT");
    activity(1, 2, "截止验证工作坊", "PUBLISHED", null, null);
    session(1, 1, "2026-11-12 14:00:00", "2026-11-12 16:00:00",
        "2026-11-11 18:00:00", 1, "PUBLISHED");
  }

  @Test
  void receiptBeforeDeadlineRemainsValidAfterWaitingForTheDatabaseLock() throws Exception {
    var receipt = admission.begin(1);
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
        return registrations.apply(1, 1, "register", receipt.receivedAt());
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> result.get(150, TimeUnit.MILLISECONDS));
      clock.setBeijing(deadline.plusSeconds(1));
      lock.commit();
      result.get(5, TimeUnit.SECONDS);
      assertEquals("REGISTERED", state(1));
      assertEquals(deadline.minusSeconds(1), jdbc.queryForObject(
          "SELECT registered_at FROM registrations WHERE participant_id = 1", LocalDateTime.class));
    } finally {
      admission.end(receipt);
    }
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
  }

  @Test
  void cutoffWaitsForAllReceiptsAndExpiresTheRemainingQueueOnlyOnce() {
    jdbc.update("UPDATE sessions SET capacity = 0 WHERE id = 1");
    registration(10, 3, 1, "WAITLIST");
    jdbc.update("UPDATE registrations SET waitlist_order = 1 WHERE id = 10");
    var first = admission.begin(1);
    try {
      var second = admission.begin(1);
      try {
        clock.setBeijing(deadline);
        registrations.closeDueWaitlists();
        assertEquals("WAITLIST", state(3));
        registrations.apply(1, 1, "waitlist", first.receivedAt());
        assertEquals("WAITLIST", state(1));
      } finally {
        admission.end(second);
      }
      assertTrue(admission.hasPendingBefore(1, Instant.MAX));
      registrations.closeDueWaitlists();
      assertEquals("WAITLIST", state(1));
      assertEquals("WAITLIST", state(3));
    } finally {
      admission.end(first);
    }
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
    registrations.closeDueWaitlists();
    registrations.closeDueWaitlists();
    assertEquals("WAITLIST_EXPIRED", state(1));
    assertEquals("WAITLIST_EXPIRED", state(3));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs l"
        + " JOIN registrations r ON r.id = l.registration_id"
        + " WHERE l.action = 'EXPIRE_WAITLIST' AND l.result = 'SUCCESS'"
        + " AND l.actor_type = 'SYSTEM' AND l.actor_id IS NULL", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void exactAndLaterDeadlineRejectNewRequestsButReturnExistingRecords(int seconds)
      throws Exception {
    registration(10, 3, 1, "WAITLIST");
    MockHttpSession participant = login("lin_zy", "PARTICIPANT");
    final MockHttpSession existing = login("chen_an", "PARTICIPANT");
    clock.setBeijing(deadline.plusSeconds(seconds));
    for (String action : new String[] {"register", "waitlist"}) {
      mvc.perform(post("/me/sessions/1/registrations").session(participant).with(csrf())
              .param("action", action).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.availability").value("CLOSED"));
    }
    registrations.closeDueWaitlists();
    mvc.perform(post("/me/sessions/1/registrations").session(existing).with(csrf())
            .param("action", "waitlist").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andExpect(jsonPath("$.registrationId").value(10))
        .andExpect(jsonPath("$.status").value("WAITLIST_EXPIRED"))
        .andExpect(jsonPath("$.canApply").value(false));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM registrations", Integer.class));
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void lateRequestsDoNotPreventClosingTheWaitlist(int seconds) {
    registration(10, 3, 1, "WAITLIST");
    var early = admission.begin(1);
    clock.setBeijing(deadline.plusSeconds(seconds));
    var late = admission.begin(1);
    try {
      try {
        registrations.closeDueWaitlists();
        assertEquals("WAITLIST", state(3));
      } finally {
        admission.end(early);
      }
      assertTrue(admission.hasPendingBefore(1, Instant.MAX));
      registrations.closeDueWaitlists();
      assertEquals("WAITLIST_EXPIRED", state(3));
    } finally {
      admission.end(late);
    }
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
  }

  private String state(long participantId) {
    return jdbc.queryForObject("SELECT status FROM registrations WHERE participant_id = ?",
        String.class, participantId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cancel", "capacity"})
  void admittedChangesPromoteBeforeTheRemainingQueueCloses(String action) {
    prepareQueue();
    var receipt = admission.begin(1);
    try {
      clock.setBeijing(deadline);
      registrations.closeDueWaitlists();
      assertEquals("WAITLIST", state(3));
      if ("cancel".equals(action)) {
        registrations.cancel(1, 1, 10, receipt.receivedAt());
        assertEquals("CANCELLED", state(1));
      } else {
        registrations.resize(2, 1, 1, "2", receipt.receivedAt());
        assertEquals("REGISTERED", state(1));
      }
      assertEquals("REGISTERED", state(3));
      assertEquals("WAITLIST", state(4));
    } finally {
      admission.end(receipt);
    }
    registrations.closeDueWaitlists();
    assertEquals("WAITLIST_EXPIRED", state(4));
    assertEquals("REGISTERED", state(3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"cancel", "capacity"})
  void httpChangesKeepTheirReceiptTimeWhileWaitingForTheSessionLock(String action)
      throws Exception {
    prepareQueue();
    boolean cancel = "cancel".equals(action);
    var actor = login(cancel ? "lin_zy" : "su_qing", cancel ? "PARTICIPANT" : "ORGANIZER");
    String path = cancel ? "/me/sessions/1/registrations/10/cancel"
        : "/organizer/activities/1/sessions/1/capacity";
    try (var executor = Executors.newSingleThreadExecutor();
        Connection lock = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
      lock.setAutoCommit(false);
      try (var statement = lock.prepareStatement("SELECT id FROM sessions WHERE id = 1 FOR UPDATE");
          var row = statement.executeQuery()) {
        assertTrue(row.next());
      }
      final var result = executor.submit(() -> mvc.perform(post(path).session(actor).with(csrf())
          .param("capacity", "2")).andExpect(status().isOk()));
      long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!admission.hasPendingBefore(1, Instant.MAX) && System.nanoTime() < timeout) {
        Thread.sleep(10);
      }
      assertTrue(admission.hasPendingBefore(1, Instant.MAX));
      clock.setBeijing(deadline);
      lock.commit();
      result.get(5, TimeUnit.SECONDS);
    }
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
    assertEquals("REGISTERED", state(3));
    registrations.closeDueWaitlists();
    assertEquals("WAITLIST_EXPIRED", state(4));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void exactAndLaterDeadlineRejectCancellationAndCapacityChanges(int seconds) throws Exception {
    prepareQueue();
    var participant = login("lin_zy", "PARTICIPANT");
    final var organizer = login("su_qing", "ORGANIZER");
    var waiting = login("chen_an", "PARTICIPANT");
    clock.setBeijing(deadline.plusSeconds(seconds));
    mvc.perform(post("/me/sessions/1/registrations/10/cancel").session(participant).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(post("/me/sessions/1/registrations/11/cancel").session(waiting).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(post("/organizer/activities/1/sessions/1/capacity").session(organizer).with(csrf())
            .param("capacity", "2")).andExpect(status().isConflict());
    assertEquals("REGISTERED", state(1));
    assertEquals("WAITLIST", state(3));
    assertEquals(1, jdbc.queryForObject("SELECT capacity FROM sessions WHERE id = 1",
        Integer.class));
    assertFalse(admission.hasPendingBefore(1, Instant.MAX));
  }

  private void prepareQueue() {
    user(4, "wang_he", "王禾", "PARTICIPANT");
    registration(10, 1, 1, "REGISTERED");
    registration(11, 3, 1, "WAITLIST");
    registration(12, 4, 1, "WAITLIST");
    jdbc.update("UPDATE registrations SET waitlist_order = id - 10 WHERE id IN (11, 12)");
  }
}
