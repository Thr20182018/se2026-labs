package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class LifecycleTestBase extends WorkshopTestBase {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
  static final LocalDateTime END = LocalDateTime.of(2026, 11, 12, 16, 0);
  static final String ACTIVITY = "/organizer/activities/1";
  static final String SCENE = ACTIVITY + "/sessions/1";

  @Autowired
  ObjectMapper json;

  private final Map<Long, MockHttpSession> actors = new HashMap<>();

  @BeforeEach
  void prepareLifecycle() {
    actors.clear();
    clock.setBeijing(NOW);
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "lu_xi", "陆曦", "ADMIN");
    user(4, "chen_an", "陈安然", "PARTICIPANT");
    user(5, "wang_he", "王禾", "PARTICIPANT");
    user(6, "xu_cheng", "许澄", "ORGANIZER");
    user(7, "zhou_ning", "周宁", "PARTICIPANT");
    user(8, "shen_yao", "沈遥", "PARTICIPANT");
    user(9, "tang_qing", "唐晴", "PARTICIPANT");
    activity(1, 2, "蓝晒工作坊", "PUBLISHED", null, null);
    futureSession(1, 1, 1);
  }

  void futureSession(long id, long activityId, int capacity) {
    session(id, activityId, "2026-11-12 14:00:00", "2026-11-12 16:00:00",
        "2026-11-11 18:00:00", capacity, "PUBLISHED");
  }

  void queued(long id, long participantId, int order) {
    registration(id, participantId, 1, "WAITLIST");
    jdbc.update("UPDATE registrations SET waitlist_order = ? WHERE id = ?", order, id);
  }

  MockHttpSession actor(long id) throws Exception {
    if (!actors.containsKey(id)) {
      var user = jdbc.queryForMap("SELECT account, role FROM users WHERE id = ?", id);
      actors.put(id, login(user.get("account").toString(), user.get("role").toString()));
    }
    return actors.get(id);
  }

  MockHttpServletRequestBuilder write(long actorId, String path) throws Exception {
    return post(path).session(actor(actorId)).with(csrf()).accept(MediaType.APPLICATION_JSON);
  }

  JsonNode submit(MockHttpServletRequestBuilder request, int expected) throws Exception {
    String payload = mvc.perform(request).andExpect(status().is(expected))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    JsonNode result = json.readTree(payload);
    assertFalse(result.path("message").asText().isBlank());
    return result;
  }

  JsonNode apply(long actorId, long sessionId, String action, int expected) throws Exception {
    return submit(write(actorId, "/me/sessions/" + sessionId + "/registrations")
        .param("action", action), expected);
  }

  String cancelPath(long sessionId, long registrationId) {
    return "/me/sessions/" + sessionId + "/registrations/" + registrationId + "/cancel";
  }

  String checkInPath(long sessionId, long registrationId) {
    return ACTIVITY + "/sessions/" + sessionId + "/registrations/" + registrationId + "/check-in";
  }

  String state(long id) {
    return jdbc.queryForObject("SELECT status FROM registrations WHERE id = ?", String.class, id);
  }

  String publication(String table, long id) {
    return jdbc.queryForObject("SELECT publication_status FROM " + table + " WHERE id = ?",
        String.class, id);
  }

  int count(String sql, Object... arguments) {
    return jdbc.queryForObject(sql, Integer.class, arguments);
  }

  int occupied(long sessionId) {
    return count("SELECT COUNT(*) FROM registrations WHERE session_id = ?"
        + " AND status IN ('REGISTERED', 'CHECKED_IN', 'ABSENT')", sessionId);
  }

  int logs(String action, String result) {
    return count("SELECT COUNT(*) FROM operation_logs WHERE action = ? AND result = ?",
        action, result);
  }

  void assertTransition(long id, String action, Long actorId, String before, String after) {
    var log = jdbc.queryForMap("SELECT actor_id, actor_type, before_value, after_value"
        + " FROM operation_logs WHERE registration_id = ? AND action = ? AND result = 'SUCCESS'",
        id, action);
    assertEquals(actorId, log.get("actor_id"));
    assertEquals(actorId == null ? "SYSTEM" : "USER", log.get("actor_type"));
    assertTrue(log.get("before_value").toString().contains(before));
    assertTrue(log.get("after_value").toString().contains(after));
  }

  void rejectSuccessLog(String action) {
    jdbc.execute("ALTER TABLE operation_logs ADD CONSTRAINT test_reject_lifecycle"
        + " CHECK (action <> '" + action + "' OR result <> 'SUCCESS')");
  }

  void restoreSuccessLog() {
    jdbc.execute("ALTER TABLE operation_logs DROP CHECK test_reject_lifecycle");
  }

  List<Integer> race(MockHttpServletRequestBuilder... requests) throws Exception {
    CountDownLatch ready = new CountDownLatch(requests.length);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(requests.length)) {
      List<Future<Integer>> pending = new ArrayList<>();
      for (var request : requests) {
        pending.add(executor.submit(() -> {
          ready.countDown();
          assertTrue(start.await(5, TimeUnit.SECONDS));
          return mvc.perform(request).andReturn().getResponse().getStatus();
        }));
      }
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> results = new ArrayList<>();
      for (var result : pending) {
        results.add(result.get(15, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      start.countDown();
    }
  }
}
