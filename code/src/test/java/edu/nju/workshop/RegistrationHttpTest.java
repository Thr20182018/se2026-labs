package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.identity.Role;
import edu.nju.workshop.identity.UserAccount;
import edu.nju.workshop.registration.RegistrationService;
import edu.nju.workshop.statistics.StatisticsService;
import edu.nju.workshop.statistics.StatisticsSummary;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RegistrationHttpTest extends WorkshopTestBase {

  @Autowired
  ObjectMapper json;

  @Autowired
  RegistrationService registrations;

  @Autowired
  StatisticsService statistics;

  private MockHttpSession participant;
  private MockHttpSession other;

  @BeforeEach
  void prepare() throws Exception {
    clock.setBeijing(LocalDateTime.of(2026, 11, 10, 10, 0));
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "lu_xi", "陆曦", "ADMIN");
    user(4, "chen_an", "陈安然", "PARTICIPANT");
    activity(1, 2, "蓝晒工作坊", "PUBLISHED", null, null);
    session(1, 1, "2026-11-12 14:00:00", "2026-11-12 16:00:00",
        "2026-11-11 18:00:00", 1, "PUBLISHED");
    participant = login("lin_zy", "PARTICIPANT");
    other = login("chen_an", "PARTICIPANT");
  }

  @RepeatedTest(10)
  void registrationReturnsCurrentStateAndRepeatedRequestsReuseItsRecord() throws Exception {
    jdbc.update("UPDATE sessions SET capacity = 2 WHERE id = 1");
    apply(participant, 1, "waitlist", 409);
    assertEquals(0, count("SELECT COUNT(*) FROM registrations"));
    JsonNode first = apply(participant, 1, "register", 200);
    assertEquals("REGISTERED", first.path("status").asText());
    assertEquals(1, first.path("remaining").asInt());
    assertEquals("OPEN", first.path("availability").asText());
    assertFalse(first.path("canApply").asBoolean());
    long id = first.path("registrationId").asLong();
    assertTrue(id > 0);
    assertEquals(LocalDateTime.of(2026, 11, 10, 10, 0), jdbc.queryForObject(
        "SELECT registered_at FROM registrations WHERE id = ?", LocalDateTime.class, id));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'REGISTER'"
        + " AND result = 'SUCCESS' AND registration_id = ? AND actor_id = 1", id));
    for (int i = 1; i < 10; i++) {
      JsonNode repeated = apply(participant, 1, "register", 200);
      assertEquals(id, repeated.path("registrationId").asLong());
      assertEquals("REGISTERED", repeated.path("status").asText());
      assertEquals(1, repeated.path("remaining").asInt());
    }
    assertEquals(1, count("SELECT COUNT(*) FROM registrations"));
    assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE status = 'REGISTERED'"));
    assertEquals(2, count("SELECT capacity FROM sessions WHERE id = 1"));
    assertSummary(1);
    assertTrue(sessionMarkup(body(participant, "/workshops"), 1).contains("已报名"));
  }

  @Test
  void fullSessionRequiresExplicitWaitlistAndRejoiningUsesTheHistoricalMaximum()
      throws Exception {
    apply(participant, 1, "register", 200);
    JsonNode full = apply(other, 1, "register", 409);
    assertEquals("WAITLIST", full.path("availability").asText());
    assertEquals("waitlist", full.path("action").asText());
    assertEquals(1, count("SELECT COUNT(*) FROM registrations"));
    long cancelled = apply(other, 1, "waitlist", 200).path("registrationId").asLong();
    jdbc.update("UPDATE registrations SET status = 'CANCELLED' WHERE id = ?", cancelled);
    user(5, "wang_he", "王禾", "PARTICIPANT");
    MockHttpSession third = login("wang_he", "PARTICIPANT");
    apply(third, 1, "waitlist", 200);
    JsonNode rejoined = apply(other, 1, "waitlist", 200);
    long id = rejoined.path("registrationId").asLong();
    assertNotEquals(cancelled, id);
    assertEquals("WAITLIST", rejoined.path("status").asText());
    assertEquals(0, rejoined.path("remaining").asInt());
    assertFalse(rejoined.path("canApply").asBoolean());
    assertEquals(List.of(1, 2, 3), jdbc.queryForList(
        "SELECT waitlist_order FROM registrations WHERE waitlist_order IS NOT NULL"
            + " ORDER BY waitlist_order", Integer.class));
    assertEquals(List.of(5L, 4L), jdbc.queryForList(
        "SELECT participant_id FROM registrations WHERE status = 'WAITLIST'"
            + " ORDER BY waitlist_order", Long.class));
    assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE status = 'REGISTERED'"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs WHERE action = 'JOIN_WAITLIST'"
        + " AND result = 'SUCCESS' AND registration_id = ?", id));
    assertTrue(sessionMarkup(body(other, "/workshops"), 1).contains("候补中"));
  }

  @Test
  void existingQueueKeepsPriorityWhenSeatsAreAvailable() throws Exception {
    registration(10, 4, 1, "WAITLIST");
    jdbc.update("UPDATE registrations SET waitlist_order = 7 WHERE id = 10");
    apply(participant, 1, "register", 409);
    assertEquals("WAITLIST", apply(participant, 1, "waitlist", 200).path("status").asText());
    assertEquals(List.of(7, 8), jdbc.queryForList(
        "SELECT waitlist_order FROM registrations ORDER BY waitlist_order", Integer.class));
    assertEquals(0, count("SELECT COUNT(*) FROM registrations WHERE status = 'REGISTERED'"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CHECKED_IN", "ABSENT", "WAITLIST_EXPIRED"})
  void terminalEffectiveRecordsStillPreventAnotherApplication(String state) throws Exception {
    registration(10, 1, 1, state);
    JsonNode result = apply(participant, 1, "register", 200);
    assertEquals(10, result.path("registrationId").asLong());
    assertEquals(state, result.path("status").asText());
    assertFalse(result.path("canApply").asBoolean());
    assertEquals(1, count("SELECT COUNT(*) FROM registrations"));
  }

  @RepeatedTest(10)
  void twentyParticipantsCompeteForOneSeatAndLosersCanThenWaitlist() throws Exception {
    List<MockHttpSession> applicants = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      user(100 + i, "applicant_" + i, "参与者" + i, "PARTICIPANT");
      applicants.add(login("applicant_" + i, "PARTICIPANT"));
    }
    CountDownLatch ready = new CountDownLatch(20);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(20)) {
      List<Future<MvcResult>> results = new ArrayList<>();
      for (MockHttpSession applicant : applicants) {
        results.add(executor.submit(() -> {
          ready.countDown();
          assertTrue(start.await(5, TimeUnit.SECONDS));
          return mvc.perform(request(applicant, 1, "register")).andReturn();
        }));
      }
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (int i = 0; i < results.size(); i++) {
        var response = results.get(i).get(15, TimeUnit.SECONDS).getResponse();
        statuses.add(response.getStatus());
        JsonNode payload = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, payload.path("remaining").asInt());
        if (response.getStatus() == 200) {
          assertEquals("REGISTERED", payload.path("status").asText());
          assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE id = ?"
              + " AND participant_id = ? AND status = 'REGISTERED'",
              payload.path("registrationId").asLong(), 100 + i));
        }
      }
      assertEquals(1, statuses.stream().filter(value -> value == 200).count());
      assertEquals(19, statuses.stream().filter(value -> value == 409).count());
      assertEquals(1, count("SELECT COUNT(*) FROM registrations"));
      assertEquals(1, count("SELECT capacity FROM sessions WHERE id = 1"));
      assertSummary(0);
      apply(applicants.get(statuses.indexOf(409)), 1, "waitlist", 200);
    }
    assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE status = 'REGISTERED'"));
    assertEquals(1, count("SELECT COUNT(*) FROM registrations WHERE status = 'WAITLIST'"));
    assertSummary(0);
  }

  @Test
  void ownRecordsIgnoreInjectedIdentityAndRolesAndCsrfAreEnforced() throws Exception {
    activity(2, 2, "其他参与者的专属记录", "PUBLISHED", null, null);
    session(2, 2, "2026-11-13 14:00:00", "2026-11-13 16:00:00",
        "2026-11-12 18:00:00", 1, "PUBLISHED");
    registration(10, 4, 2, "REGISTERED");
    mvc.perform(request(participant, 1, "register").param("participantId", "4"))
        .andExpect(status().isOk());
    assertEquals(1L, jdbc.queryForObject(
        "SELECT participant_id FROM registrations WHERE session_id = 1", Long.class));
    String own = body(participant, "/me/registrations?participantId=4");
    assertTrue(own.contains("蓝晒工作坊"));
    assertTrue(own.contains("报名成功"));
    assertFalse(own.contains("其他参与者的专属记录"));
    for (MockHttpSession actor : List.of(login("su_qing", "ORGANIZER"), login("lu_xi", "ADMIN"))) {
      mvc.perform(request(actor, 1, "register")).andExpect(status().isForbidden());
      mvc.perform(get("/me/registrations").session(actor)).andExpect(status().isForbidden());
    }
    mvc.perform(post("/me/sessions/1/registrations").session(other)
            .param("action", "waitlist").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    mvc.perform(post("/me/sessions/1/registrations").with(csrf()).param("action", "register"))
        .andExpect(status().is3xxRedirection());
    assertEquals(2, count("SELECT COUNT(*) FROM registrations"));
  }

  @Test
  void invalidActionUnknownAndUnpublishedSessionsAndCancellationAreRejected() throws Exception {
    apply(participant, 1, "unknown", 400);
    apply(participant, 999, "register", 404);
    jdbc.update("UPDATE sessions SET publication_status = 'PENDING' WHERE id = 1");
    apply(participant, 1, "register", 404);
    jdbc.update("UPDATE sessions SET publication_status = 'CANCELLED' WHERE id = 1");
    assertEquals("CANCELLED", apply(participant, 1, "register", 409)
        .path("availability").asText());
    assertEquals(0, count("SELECT COUNT(*) FROM registrations"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'FAILURE'"
        + " AND (failure_reason IS NULL OR failure_reason = '')"));
  }

  @ParameterizedTest
  @CsvSource({"register, 1, REGISTER", "waitlist, 0, JOIN_WAITLIST"})
  void successLogFailureRollsBackRegistrationAndAllowsRetry(String action, int capacity,
      String logAction) throws Exception {
    jdbc.update("UPDATE sessions SET capacity = ? WHERE id = 1", capacity);
    jdbc.execute("ALTER TABLE operation_logs ADD CONSTRAINT test_reject_registration"
        + " CHECK (action NOT IN ('REGISTER', 'JOIN_WAITLIST') OR result <> 'SUCCESS')");
    try {
      assertThrows(RuntimeException.class, () -> registrations.apply(1, 1, action, clock.now()));
    } finally {
      jdbc.execute("ALTER TABLE operation_logs DROP CHECK test_reject_registration");
    }
    assertEquals(0, count("SELECT COUNT(*) FROM registrations"));
    assertEquals(0, count("SELECT COUNT(*) FROM operation_logs WHERE result = 'SUCCESS'"));
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs"
        + " WHERE action = ? AND result = 'FAILURE'", logAction));
    long id = apply(participant, 1, action, 200).path("registrationId").asLong();
    assertEquals(1, count("SELECT COUNT(*) FROM operation_logs"
        + " WHERE action = ? AND result = 'SUCCESS' AND registration_id = ?", logAction, id));
  }

  private JsonNode apply(MockHttpSession actor, long sessionId, String action, int expected)
      throws Exception {
    String payload = mvc.perform(request(actor, sessionId, action)).andExpect(status().is(expected))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    JsonNode result = json.readTree(payload);
    assertFalse(result.path("message").asText().isBlank());
    for (String field : List.of("registrationId", "status", "statusLabel", "remaining",
        "availability", "availabilityLabel", "action", "actionLabel", "canApply")) {
      assertTrue(result.has(field), "响应应包含 " + field);
    }
    return result;
  }

  private MockHttpServletRequestBuilder request(MockHttpSession actor, long sessionId,
      String action) {
    return post("/me/sessions/" + sessionId + "/registrations").session(actor).with(csrf())
        .param("action", action).accept(MediaType.APPLICATION_JSON);
  }

  private String sessionMarkup(String page, long sessionId) {
    var match = Pattern.compile("(?s)<details\\b(?=[^>]*\\bid=\"session-" + sessionId
        + "\")[^>]*>.*?</details>").matcher(page);
    assertTrue(match.find());
    return match.group();
  }

  private int count(String sql, Object... arguments) {
    return jdbc.queryForObject(sql, Integer.class, arguments);
  }

  private void assertSummary(long remaining) {
    var organizer = new AccountUser(new UserAccount(2, "su_qing", "", "苏清和", Role.ORGANIZER));
    assertEquals(new StatisticsSummary(1, 0, 0, remaining),
        statistics.search(organizer, 1L, 1L, 1).pageData().summary());
  }
}
