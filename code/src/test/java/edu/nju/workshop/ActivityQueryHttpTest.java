package edu.nju.workshop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class ActivityQueryHttpTest extends WorkshopTestBase {

  private MockHttpSession participant;

  @BeforeEach
  void participant() throws Exception {
    user(1, "lin_zy", "林知远", "PARTICIPANT");
    user(2, "su_qing", "苏清和", "ORGANIZER");
    participant = login("lin_zy", "PARTICIPANT");
    clock.setBeijing(LocalDateTime.of(2026, 11, 10, 10, 0));
  }

  @Test
  void keywordDateAndAvailabilityIntersect() throws Exception {
    activity(1, 2, "蓝晒手作", "PUBLISHED", null, null);
    activity(2, 2, "声音采集", "PUBLISHED", null, null);
    session(1, 1, "2026-11-12 14:00:00", "2026-11-12 16:00:00", "2026-11-11 18:00:00",
        8, "PUBLISHED");
    session(2, 1, "2026-11-20 14:00:00", "2026-11-20 16:00:00", "2026-11-19 18:00:00",
        8, "PUBLISHED");
    session(3, 2, "2026-11-12 09:00:00", "2026-11-12 11:00:00", "2026-11-11 18:00:00",
        2, "PUBLISHED");
    registration(1, 1, 3, "REGISTERED");
    registration(2, 2, 3, "REGISTERED");
    String combined = search("蓝晒", "2026-11-10", "2026-11-15", "open");
    assertTrue(combined.contains("蓝晒手作"));
    assertFalse(combined.contains("声音采集"));
    assertFalse(combined.contains("11月20日"));
    String full = search("", "2026-11-12", "2026-11-12", "waitlist");
    assertTrue(full.contains("声音采集"));
    assertFalse(full.contains("蓝晒手作"));
  }

  @Test
  void dateRangeIncludesBothEndsAndRejectsBadInput() throws Exception {
    activity(1, 2, "边界场次", "PUBLISHED", null, null);
    session(1, 1, "2026-11-10 09:00:00", "2026-11-10 11:00:00", "2026-11-09 18:00:00",
        4, "PUBLISHED");
    session(2, 1, "2026-11-12 09:00:00", "2026-11-12 11:00:00", "2026-11-11 18:00:00",
        4, "PUBLISHED");
    session(3, 1, "2026-11-14 09:00:00", "2026-11-14 11:00:00", "2026-11-13 18:00:00",
        4, "PUBLISHED");
    String inside = search("", "2026-11-10", "2026-11-12", "");
    assertTrue(inside.contains("11月10日"));
    assertTrue(inside.contains("11月12日"));
    assertFalse(inside.contains("11月14日"));
    String single = search("", "2026-11-14", "2026-11-14", "");
    assertTrue(single.contains("11月14日"));
    assertFalse(single.contains("11月10日"));
    String reversed = search("", "2026-11-14", "2026-11-10", "");
    assertTrue(reversed.contains("开始日期不能晚于结束日期"));
    assertFalse(reversed.contains("11月12日"));
    String bad = search("", "2026-13-01", "", "");
    assertTrue(bad.contains("日期格式不正确，请使用 YYYY-MM-DD"));
  }

  @Test
  void deadlineEqualityCapacityAndCancellationUseOneMoment() throws Exception {
    activity(1, 2, "容量场次", "PUBLISHED", null, null);
    activity(2, 2, "截止场次", "PUBLISHED", null, null);
    activity(3, 2, "取消场次", "CANCELLED", null, null);
    activity(4, 2, "单独取消", "PUBLISHED", null, null);
    session(1, 1, "2026-11-18 14:00:00", "2026-11-18 16:00:00", "2026-11-17 18:00:00",
        5, "PUBLISHED");
    session(2, 2, "2026-11-18 15:00:00", "2026-11-18 17:00:00", "2026-11-10 10:00:00",
        5, "PUBLISHED");
    session(3, 3, "2026-11-19 15:00:00", "2026-11-19 17:00:00", "2026-11-18 18:00:00",
        5, "PUBLISHED");
    session(4, 4, "2026-11-19 18:00:00", "2026-11-19 20:00:00", "2026-11-18 18:00:00",
        5, "CANCELLED");
    registration(1, 1, 1, "REGISTERED");
    registration(2, 2, 1, "CHECKED_IN");
    user(3, "chen_an", "陈安然", "PARTICIPANT");
    registration(3, 3, 1, "ABSENT");
    user(4, "wang_he", "王禾", "PARTICIPANT");
    registration(4, 4, 1, "WAITLIST");
    user(5, "xu_lin", "许林", "PARTICIPANT");
    registration(5, 5, 1, "CANCELLED");
    user(6, "guest", "访客", "PARTICIPANT");
    registration(6, 6, 1, "WAITLIST_EXPIRED");
    String open = search("", "", "", "open");
    assertTrue(open.contains("容量场次"));
    assertTrue(open.contains("剩余 2 / 5 个名额"));
    assertFalse(open.contains("截止场次"));
    assertFalse(open.contains("取消场次"));
    assertFalse(open.contains("单独取消"));
    clock.setBeijing(LocalDateTime.of(2026, 11, 10, 9, 59, 59));
    assertTrue(search("", "", "", "open").contains("截止场次"));
    clock.setBeijing(LocalDateTime.of(2026, 11, 10, 10, 0, 0));
    String closed = search("", "", "", "closed");
    assertTrue(closed.contains("截止场次"));
    assertFalse(closed.contains("取消场次"));
    assertFalse(closed.contains("容量场次"));
    String all = search("", "", "", "");
    assertTrue(all.contains("已取消"));
    assertTrue(all.indexOf("容量场次") < all.indexOf("截止场次"));
    assertTrue(all.indexOf("截止场次") < all.indexOf("取消场次"));
    assertTrue(all.indexOf("取消场次") < all.indexOf("单独取消"));
  }

  @Test
  void hiddenAndPendingRevisionStayOutOfThePublicList() throws Exception {
    activity(1, 2, "公开名称", "PUBLISHED", "待审核名称不应出现", "待审核简介不应出现");
    activity(2, 2, "草稿活动", "DRAFT", null, null);
    activity(3, 2, "待审核活动", "PENDING", null, null);
    activity(4, 2, "驳回活动", "REJECTED", null, null);
    session(1, 1, "2026-11-18 14:00:00", "2026-11-18 16:00:00", "2026-11-17 18:00:00",
        6, "PUBLISHED");
    session(2, 1, "2026-11-28 14:00:00", "2026-11-28 16:00:00", "2026-11-27 18:00:00",
        6, "PENDING");
    session(3, 2, "2026-11-18 10:00:00", "2026-11-18 12:00:00", "2026-11-17 18:00:00",
        6, "DRAFT");
    session(4, 3, "2026-11-18 11:00:00", "2026-11-18 12:00:00", "2026-11-17 18:00:00",
        6, "PENDING");
    session(5, 4, "2026-11-18 12:00:00", "2026-11-18 13:00:00", "2026-11-17 18:00:00",
        6, "REJECTED");
    jdbc.update("UPDATE activities SET description = ? WHERE id = 1", "公开简介保留");
    String page = search("公开", "", "", "");
    assertTrue(page.contains("公开名称"));
    assertTrue(page.contains("公开简介保留"));
    assertFalse(page.contains("待审核名称不应出现"));
    assertFalse(page.contains("待审核简介不应出现"));
    assertFalse(page.contains("草稿活动"));
    assertFalse(page.contains("11月28日"));
    assertTrue(search("待审核名称不应出现", "", "", "").contains("暂无符合条件的场次"));
    assertTrue(search("公开简介保留", "", "", "").contains("暂无符合条件的场次"));
    assertTrue(search("不存在的名字", "", "", "").contains("暂无符合条件的场次"));
  }

  @Test
  void literalPercentAndDatabasePaging() throws Exception {
    activity(1, 2, "100%体验", "PUBLISHED", null, null);
    activity(2, 2, "普通手作", "PUBLISHED", null, null);
    session(1, 1, "2026-11-12 09:00:00", "2026-11-12 10:00:00", "2026-11-11 18:00:00",
        4, "PUBLISHED");
    session(2, 2, "2026-11-12 11:00:00", "2026-11-12 12:00:00", "2026-11-11 18:00:00",
        4, "PUBLISHED");
    String literal = search("%", "", "", "");
    assertTrue(literal.contains("100%体验"));
    assertFalse(literal.contains("普通手作"));
    for (int i = 3; i <= 10; i++) {
      activity(i, 2, "加页" + i, "PUBLISHED", null, null);
      String day = String.format("2026-11-%02d", 10 + i);
      session(i, i, day + " 08:00:00", day + " 09:00:00", "2026-11-10 18:00:00", 4, "PUBLISHED");
    }
    String first = search("", "", "", "", "1");
    String second = search("", "", "", "", "2");
    assertTrue(first.contains("共 10 个场次"));
    assertFalse(first.contains("加页10"));
    assertTrue(second.contains("加页10"));
    assertFalse(second.contains("100%体验"));
  }

  private String search(String keyword, String from, String to, String availability)
      throws Exception {
    return search(keyword, from, to, availability, "");
  }

  private String search(String keyword, String from, String to, String availability, String page)
      throws Exception {
    MvcResult result = mvc.perform(get("/workshops").session(participant)
        .param("keyword", keyword)
        .param("dateFrom", from)
        .param("dateTo", to)
        .param("availability", availability)
        .param("page", page))
        .andExpect(status().isOk())
        .andReturn();
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
