package edu.nju.workshop.activity;

/** 活动与场次从草稿、审核到发布或取消的管理状态. */
public enum PublicationStatus {
  DRAFT("草稿"), PENDING("待审核"), REJECTED("已驳回"),
  PUBLISHED("已发布"), CANCELLED("已取消");

  private final String label;

  PublicationStatus(String label) {
    this.label = label;
  }

  /** 返回发布状态在管理页面中的中文标签. */
  public String getLabel() {
    return label;
  }
}
