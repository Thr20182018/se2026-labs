package edu.nju.workshop.activity;

public enum Availability {
  OPEN("可报名"),
  WAITLIST("已满可候补"),
  CLOSED("已截止"),
  CANCELLED("已取消");

  private final String label;

  Availability(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** 查询参数。空值表示不筛选；取消场次不作为筛选项。 */
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
