package edu.nju.workshop.activity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 工作坊列表中的场次展示数据及日期、名额和操作标签. */
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

  /** 返回容量扣除占用名额人数后的剩余名额. */
  public int remaining() {
    return capacity - registeredCount;
  }

  /** 返回场次开始日期的月份标签. */
  public String monthLabel() {
    return start.getMonthValue() + "月";
  }

  /** 返回场次开始日期在当月的日序号. */
  public int dayOfMonth() {
    return start.getDayOfMonth();
  }

  /** 返回场次开始日期的中文星期标签. */
  public String weekdayLabel() {
    return WEEKDAYS[start.getDayOfWeek().getValue()];
  }

  /** 返回起止时刻，跨日场次补充结束日期. */
  public String clockRange() {
    String endDate = start.toLocalDate().equals(end.toLocalDate()) ? "" : date(end) + " ";
    return clock(start) + "–" + endDate + clock(end);
  }

  /** 返回开始日期，跨年场次同时显示年份. */
  public String startDateLabel() {
    return date(start);
  }

  /** 返回报名截止的月日及小时分钟. */
  public String deadlineLabel() {
    return deadline.getMonthValue() + "月" + deadline.getDayOfMonth() + "日 " + clock(deadline);
  }

  /** 组合场次详情所需的开始日期、星期和起止时刻. */
  public String detailTime() {
    return startDateLabel() + "（" + weekdayLabel() + "）" + clockRange();
  }

  /** 根据报名情况返回报名、候补或不可申请的按钮文案. */
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
