package edu.nju.workshop;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
class AuthHttpTest extends WorkshopTestBase {

  @LocalServerPort
  int port;

  @BeforeEach
  void accounts() {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    user(3, "zhao_ming", "赵明远", "ORGANIZER");
    user(4, "he_qm", "何启明", "ADMIN");
    activity(1, 2, "仅苏清和可见", "PUBLISHED", null, null);
    activity(2, 3, "仅赵明远可见", "PUBLISHED", null, null);
  }

  @Test
  void wrongPasswordAndUnknownAccountShareOneMessage() throws Exception {
    mvc.perform(post("/login").param("username", "lin_zy").param("password", "nope").with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error"));
    mvc.perform(post("/login").param("username", "missing").param("password", PASSWORD)
        .with(csrf()))
        .andExpect(redirectedUrl("/login?error"));
    mvc.perform(get("/login").param("error", ""))
        .andExpect(content().string(containsString("账号或密码错误，请重新输入")));
  }

  @Test
  void threeRolesSeeNameAndOnlyTheirNavigation() throws Exception {
    String participant = body(login("lin_zy", "PARTICIPANT"), "/workshops");
    assertTrue(participant.contains("林知远"));
    assertTrue(participant.contains("参与者"));
    assertTrue(participant.contains("我的报名"));
    assertFalse(participant.contains("活动管理"));
    assertFalse(participant.contains("审核管理"));

    String organizer = body(login("su_qing", "ORGANIZER"), "/workshops");
    assertTrue(organizer.contains("苏清和"));
    assertTrue(organizer.contains("组织者"));
    assertTrue(organizer.contains("活动管理"));
    assertFalse(organizer.contains("审核管理"));

    String admin = body(login("he_qm", "ADMIN"), "/workshops");
    assertTrue(admin.contains("何启明"));
    assertTrue(admin.contains("管理员"));
    assertTrue(admin.contains("审核管理"));
    assertFalse(admin.contains(HASH));
  }

  @Test
  void wrongRoleIsRejectedWithoutTheProtectedPage() throws Exception {
    denied(login("lin_zy", "PARTICIPANT"), "/admin/reviews", "审核管理");
    denied(login("lin_zy", "PARTICIPANT"), "/organizer/activities", "活动管理");
    denied(login("lin_zy", "PARTICIPANT"), "/organizer/activities/1", "仅苏清和可见");
    denied(login("su_qing", "ORGANIZER"), "/admin/reviews", "审核管理");
    denied(login("su_qing", "ORGANIZER"), "/me/registrations", "我的报名");
    denied(login("he_qm", "ADMIN"), "/organizer/activities", "活动管理");
    assertTrue(body(login("he_qm", "ADMIN"), "/admin/reviews").contains("审核管理"));
    assertTrue(body(login("su_qing", "ORGANIZER"), "/organizer/activities").contains("活动管理"));
  }

  @Test
  void organizerCannotReadAnotherOrganizersActivityByChangingId() throws Exception {
    int before = jdbc.queryForObject("SELECT COUNT(*) FROM activities", Integer.class);
    assertTrue(body(login("su_qing", "ORGANIZER"), "/organizer/activities/1")
        .contains("仅苏清和可见"));
    denied(login("zhao_ming", "ORGANIZER"), "/organizer/activities/1", "仅苏清和可见");
    denied(login("su_qing", "ORGANIZER"), "/organizer/activities/2", "仅赵明远可见");
    denied(login("su_qing", "ORGANIZER"), "/organizer/activities/99", "仅赵明远可见");
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM activities", Integer.class));
  }

  @Test
  void anonymousQueryRedirectsToLogin() throws Exception {
    mvc.perform(get("/workshops"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "_csrf=invalid"})
  void logoutRequiresCsrfAndEndsTheSession(String rejectedForm) throws Exception {
    // 真实 HTTP 请求由容器完成 POST 转发，断言最终状态及页面。
    try (HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
      String loginPage = request(client, "/login", null).body();
      HttpResponse<String> login = request(client, "/login",
          "username=lin_zy&password=" + PASSWORD + "&_csrf=" + csrfToken(loginPage));
      assertEquals(302, login.statusCode());
      assertTrue(login.headers().firstValue("Location").orElseThrow().endsWith("/workshops"));
      HttpResponse<String> denied = request(client, "/logout", rejectedForm);
      assertEquals(403, denied.statusCode());
      assertTrue(denied.body().contains("无权访问"), denied.body());
      HttpResponse<String> workshops = request(client, "/workshops", null);
      assertEquals(200, workshops.statusCode());
      HttpResponse<String> logout = request(
          client, "/logout", "_csrf=" + csrfToken(workshops.body()));
      assertEquals(302, logout.statusCode());
      assertTrue(logout.headers().firstValue("Location").orElseThrow().endsWith("/login?logout"));
      assertEquals(302, request(client, "/workshops", null).statusCode());
      assertTrue(request(client, "/login?logout", null).body().contains("您已退出登录"));
    }
  }

  private HttpResponse<String> request(HttpClient client, String path, String form)
      throws Exception {
    HttpRequest.Builder request = HttpRequest
        .newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
    if (form != null) {
      request.header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(form));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static String csrfToken(String page) {
    var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page);
    assertTrue(token.find(), "页面缺少 CSRF token");
    return URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
  }

  private void denied(MockHttpSession session, String path, String secret) throws Exception {
    MvcResult result = mvc.perform(get(path).session(session)).andReturn();
    assertEquals(403, result.getResponse().getStatus());
    String page = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    if ("/access-denied".equals(result.getResponse().getForwardedUrl())) {
      page = mvc.perform(get("/access-denied").session(session))
          .andReturn()
          .getResponse()
          .getContentAsString(StandardCharsets.UTF_8);
    }
    assertTrue(page.contains("无权访问"), page);
    assertFalse(page.contains(secret), page);
  }
}
