package edu.nju.workshop.activity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record SessionRow(
    long id,
    String name,
    String description,
    LocalDateTime start,
    LocalDateTime end,
    LocalDateTime deadline,
    String location,
    int capacity,
    int registeredCount,
    Availability availability) {

  private static final String[] WEEKDAYS = {
      "", "周一", "周二", "周三", "周四", "周五", "周六", "周日"
  };

  public int remaining() {
    return capacity - registeredCount;
  }

  public String monthLabel() {
    return start.getMonthValue() + "月";
  }

  public int dayOfMonth() {
    return start.getDayOfMonth();
  }

  public String weekdayLabel() {
    return WEEKDAYS[start.getDayOfWeek().getValue()];
  }

  public String clockRange() {
    String endDate = start.toLocalDate().equals(end.toLocalDate()) ? "" : date(end) + " ";
    return clock(start) + "–" + endDate + clock(end);
  }

  public String startDateLabel() {
    return date(start);
  }

  public String deadlineLabel() {
    return deadline.getMonthValue() + "月" + deadline.getDayOfMonth() + "日 " + clock(deadline);
  }

  public String detailTime() {
    return startDateLabel() + "（" + weekdayLabel() + "）" + clockRange();
  }

  public String actionLabel() {
    return switch (availability) {
      case OPEN -> "报名此场次";
      case WAITLIST -> "加入候补";
      case CLOSED -> "报名已截止";
      case CANCELLED -> "场次已取消";
      default -> throw new IllegalStateException("未知报名情况");
    };
  }

  private String date(LocalDateTime time) {
    String pattern = start.getYear() == end.getYear() ? "M月d日" : "yyyy年M月d日";
    return time.format(DateTimeFormatter.ofPattern(pattern));
  }

  private static String clock(LocalDateTime time) {
    return String.format("%02d:%02d", time.getHour(), time.getMinute());
  }
}
