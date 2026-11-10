package edu.nju.workshop.activity;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record ActivityQuery(
    String keyword,
    LocalDate dateFrom,
    LocalDate dateTo,
    Availability availability,
    int page) {

  public static ActivityQuery parse(
      String keyword, String dateFrom, String dateTo, String availability, String page) {
    LocalDate from = parseDate(dateFrom);
    LocalDate to = parseDate(dateTo);
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("开始日期不能晚于结束日期");
    }
    return new ActivityQuery(
        clean(keyword), from, to, Availability.fromParam(availability), parsePage(page));
  }

  private static String clean(String keyword) {
    if (keyword == null) {
      return null;
    }
    String trimmed = keyword.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim().replace('/', '-'));
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("日期格式不正确，请使用 YYYY-MM-DD");
    }
  }

  private static int parsePage(String raw) {
    if (raw == null || raw.isBlank()) {
      return 1;
    }
    try {
      int page = Integer.parseInt(raw.trim());
      if (page < 1) {
        throw new IllegalArgumentException("页码不正确");
      }
      return page;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("页码不正确");
    }
  }
}
