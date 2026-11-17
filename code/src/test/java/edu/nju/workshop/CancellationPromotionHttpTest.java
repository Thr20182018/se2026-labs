package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CancellationPromotionHttpTest extends LifecycleTestBase {

  @RepeatedTest(10)
  void cancellationPromotesOnlyTheEarliestWaitlistRecordInEachIndependentRound()
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 9);
    queued(30, 5, 7);
    submit(write(1, cancelPath(1, 10)), 200);
    assertEquals("CANCELLED", state(10));
    assertEquals("WAITLIST", state(20));
    assertEquals("REGISTERED", state(30));
    assertEquals(1, occupied(1));
    assertEquals(1, logs("CANCEL_REGISTRATION", "SUCCESS"));
    assertEquals(1, logs("PROMOTE_WAITLIST", "SUCCESS"));
    assertTransition(10, "CANCEL_REGISTRATION", 1L, "REGISTERED", "CANCELLED");
    assertTransition(30, "PROMOTE_WAITLIST", null, "WAITLIST", "REGISTERED");
  }

  @Test
  void leavingWaitlistPreservesSeatsAndRejoiningGoesAfterTheHistoricalMaximum()
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    queued(30, 5, 2);
    submit(write(4, cancelPath(1, 20)), 200);
    assertEquals("REGISTERED", state(10));
    assertEquals("WAITLIST", state(30));
    assertEquals(1, occupied(1));
    assertEquals(0, logs("PROMOTE_WAITLIST", "SUCCESS"));
    assertTransition(20, "LEAVE_WAITLIST", 4L, "WAITLIST", "CANCELLED");
    long newId = apply(4, 1, "waitlist", 200).path("registrationId").asLong();
    assertNotEquals(20, newId);
    assertEquals(3, count("SELECT waitlist_order FROM registrations WHERE id = ?", newId));
    submit(write(4, cancelPath(1, 20)), 200);
    assertEquals("WAITLIST", state(newId));
    assertEquals(1, logs("LEAVE_WAITLIST", "SUCCESS"));
    submit(write(1, cancelPath(1, 10)), 200);
    assertEquals("REGISTERED", state(30));
    assertEquals("WAITLIST", state(newId));
    assertTrue(body(actor(4), "/me/registrations").contains("候补"));
  }

  @Test
  void cancelledHistoryKeepsItsIdentityAfterASeatIsReleasedAndReapplied() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    String initial = body(actor(1), "/me/registrations");
    assertTrue(initial.contains(cancelPath(1, 10)));
    submit(write(1, cancelPath(1, 10)), 200);
    assertEquals(0, occupied(1));
    String history = body(actor(1), "/me/registrations");
    assertTrue(history.contains("已取消"));
    assertTrue(history.contains("/workshops?selected=1"));
    assertFalse(history.contains("报名编号"));
    assertFalse(history.contains("场次编号"));
    long newId = apply(1, 1, "register", 200).path("registrationId").asLong();
    assertNotEquals(10, newId);
    submit(write(1, cancelPath(1, 10)), 200);
    assertEquals("REGISTERED", state(newId));
    assertEquals("CANCELLED", state(10));
    assertEquals(1, occupied(1));
    assertEquals(1, logs("CANCEL_REGISTRATION", "SUCCESS"));
  }

  @Test
  void reapplyingFromHistoryOpensTheSelectedSessionBeyondTheFirstPage() throws Exception {
    for (int id = 2; id <= 12; id++) {
      futureSession(id, 1, 1);
    }
    registration(10, 1, 12, "REGISTERED");
    submit(write(1, cancelPath(12, 10)), 200);
    assertTrue(body(actor(1), "/me/registrations").contains("/workshops?selected=12"));
    String target = body(actor(1), "/workshops?selected=12");
    assertTrue(target.contains("id=\"session-12\" open=\"open\""));
    assertTrue(target.contains("action=\"/me/sessions/12/registrations\""));
    assertFalse(target.contains("id=\"session-1\""));
    apply(1, 12, "register", 200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CHECKED_IN", "ABSENT", "WAITLIST_EXPIRED"})
  void participantCannotCancelOtherStates(String state) throws Exception {
    registration(10, 1, 1, state);
    submit(write(1, cancelPath(1, 10)), 409);
    assertEquals(state, state(10));
    assertEquals(0, logs("CANCEL_REGISTRATION", "SUCCESS"));
    assertFalse(body(actor(1), "/me/registrations").contains(cancelPath(1, 10)));
  }

  @Test
  void capacityCountsRegisteredCheckedInAndAbsentAndPromotesOnlyAvailablePlaces()
      throws Exception {
    jdbc.update("UPDATE sessions SET capacity = 5 WHERE id = 1");
    registration(10, 1, 1, "REGISTERED");
    registration(20, 4, 1, "CHECKED_IN");
    registration(30, 5, 1, "ABSENT");
    queued(40, 7, 3);
    queued(50, 8, 1);
    queued(60, 9, 2);
    var refused = submit(write(2, SCENE + "/capacity").param("capacity", "2"), 409);
    assertTrue(refused.path("message").asText().contains("3"));
    assertEquals(5, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(3, occupied(1));
    submit(write(2, SCENE + "/capacity").param("capacity", "3"), 200);
    assertEquals(3, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(0, logs("PROMOTE_WAITLIST", "SUCCESS"));
    submit(write(2, SCENE + "/capacity").param("capacity", "5"), 200);
    assertEquals(5, occupied(1));
    assertEquals("REGISTERED", state(50));
    assertEquals("REGISTERED", state(60));
    assertEquals("WAITLIST", state(40));
    assertEquals("PUBLISHED", publication("activities", 1));
    assertEquals("PUBLISHED", publication("sessions", 1));
    assertEquals(0, count("SELECT review_version FROM sessions WHERE id = 1"));
    assertEquals(2, logs("UPDATE_CAPACITY", "SUCCESS"));
    assertEquals(2, logs("PROMOTE_WAITLIST", "SUCCESS"));
    assertTransition(50, "PROMOTE_WAITLIST", null, "WAITLIST", "REGISTERED");
    submit(write(2, SCENE + "/capacity").param("capacity", "7"), 200);
    assertEquals(6, occupied(1));
    assertEquals("REGISTERED", state(40));
    assertEquals(7, count("SELECT capacity FROM sessions WHERE id = 1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "1.5", "many", "2147483648"})
  void malformedCapacityDoesNotChangeThePublishedSession(String capacity) throws Exception {
    submit(write(2, SCENE + "/capacity").param("capacity", capacity), 400);
    assertEquals(1, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(0, logs("UPDATE_CAPACITY", "SUCCESS"));
  }

  @Test
  void emptySessionAllowsZeroCapacity() throws Exception {
    submit(write(2, SCENE + "/capacity").param("capacity", "0"), 200);
    assertEquals(0, count("SELECT capacity FROM sessions WHERE id = 1"));
    apply(1, 1, "register", 409);
    assertEquals("WAITLIST", apply(1, 1, "waitlist", 200).path("status").asText());
  }

  @Test
  void ownersRolesAndCsrfAreEnforcedForCancellationAndCapacity() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    submit(write(4, cancelPath(1, 10)), 403);
    mvc.perform(write(2, cancelPath(1, 10))).andExpect(status().isForbidden());
    mvc.perform(post(cancelPath(1, 10)).session(actor(1)))
        .andExpect(status().isForbidden());
    for (long actorId : List.of(1L, 3L, 6L)) {
      mvc.perform(write(actorId, SCENE + "/capacity").param("capacity", "2"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(post(SCENE + "/capacity").session(actor(2)).param("capacity", "2"))
        .andExpect(status().isForbidden());
    assertEquals("REGISTERED", state(10));
    assertEquals(1, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(3, logs("CANCEL_REGISTRATION", "FAILURE"));
    assertEquals(4, logs("UPDATE_CAPACITY", "FAILURE"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
  }

  @Test
  void cancellingResizingAndSigningUpCannotTakePlacesAheadOfTheQueue() throws Exception {
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    queued(30, 5, 2);
    List<Integer> results = race(write(1, cancelPath(1, 10)),
        write(2, SCENE + "/capacity").param("capacity", "2"),
        write(7, "/me/sessions/1/registrations").param("action", "register"));
    assertEquals(List.of(200, 200, 409), results);
    assertEquals("CANCELLED", state(10));
    assertEquals("REGISTERED", state(20));
    assertEquals("REGISTERED", state(30));
    assertEquals(2, occupied(1));
    assertEquals(2, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(0, count("SELECT COUNT(*) FROM registrations WHERE participant_id = 7"));
    assertEquals(2, logs("PROMOTE_WAITLIST", "SUCCESS"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"cancel", "capacity"})
  void promotionLogFailureRollsBackTheEntireCancellationOrResize(String operation)
      throws Exception {
    registration(10, 1, 1, "REGISTERED");
    queued(20, 4, 1);
    boolean cancellation = operation.equals("cancel");
    var request = cancellation ? write(1, cancelPath(1, 10))
        : write(2, SCENE + "/capacity").param("capacity", "2");
    rejectSuccessLog("PROMOTE_WAITLIST");
    try {
      assertThrows(ServletException.class, () -> mvc.perform(request));
    } finally {
      restoreSuccessLog();
    }
    assertEquals("REGISTERED", state(10));
    assertEquals("WAITLIST", state(20));
    assertEquals(1, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
    assertEquals(1, logs(cancellation ? "CANCEL_REGISTRATION" : "UPDATE_CAPACITY", "FAILURE"));
    submit(cancellation ? write(1, cancelPath(1, 10))
        : write(2, SCENE + "/capacity").param("capacity", "2"), 200);
    assertEquals("REGISTERED", state(20));
    assertEquals(cancellation ? "CANCELLED" : "REGISTERED", state(10));
    assertEquals(cancellation ? 1 : 2, occupied(1));
  }
}
