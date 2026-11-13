package edu.nju.workshop.activity;

public enum PublicationStatus {
  DRAFT("草稿"), PENDING("待审核"), REJECTED("已驳回"),
  PUBLISHED("已发布"), CANCELLED("已取消");

  private final String label;

  PublicationStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
