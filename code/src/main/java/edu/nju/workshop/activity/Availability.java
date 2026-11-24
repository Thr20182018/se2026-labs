package edu.nju.workshop.activity;

/** 工作坊列表根据取消状态、报名截止时间和名额情况展示的报名状态. */
public enum Availability {
  OPEN("可报名"),
  WAITLIST("已满可候补"),
  CLOSED("已截止"),
  CANCELLED("已取消");

  private final String label;

  Availability(String label) {
    this.label = label;
  }

  /** 返回报名情况在页面中的中文标签. */
  public String getLabel() {
    return label;
  }

  /** 解析报名情况筛选参数，空值表示不筛选，取消场次不作为筛选项. */
  public static Availability fromParam(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw) {
      case "open" -> OPEN;
      case "waitlist" -> WAITLIST;
      case "closed" -> CLOSED;
      default -> throw new IllegalArgumentException("报名情况无效");
    };
  }
}
