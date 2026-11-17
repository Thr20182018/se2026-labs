package edu.nju.workshop.activity;

public enum ReviewKind {
  FIRST("首次发布"), REVISION("内容修改"), SESSION("新增场次");

  private final String label;

  ReviewKind(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
