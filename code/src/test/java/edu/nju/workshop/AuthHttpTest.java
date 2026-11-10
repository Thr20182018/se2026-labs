package edu.nju.workshop;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthHttpTest extends WorkshopTestBase {

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

  @Test
  void logoutRequiresCsrfAndEndsTheSession() throws Exception {
    MockHttpSession session = login("lin_zy", "PARTICIPANT");
    mvc.perform(post("/logout").session(session))
        .andExpect(status().isForbidden())
        .andExpect(forwardedUrl("/access-denied"));
    mvc.perform(post("/logout").session(session).with(csrf()))
        .andExpect(redirectedUrl("/login?logout"))
        .andExpect(unauthenticated());
    mvc.perform(get("/workshops").session(session))
        .andExpect(status().is3xxRedirection());
    mvc.perform(get("/login").param("logout", ""))
        .andExpect(content().string(containsString("您已退出登录")));
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
