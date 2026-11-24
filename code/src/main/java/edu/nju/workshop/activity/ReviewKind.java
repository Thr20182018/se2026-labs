package edu.nju.workshop.activity;

/** 管理员审核队列中的首次发布、内容修改和新增场次三类申请. */
public enum ReviewKind {
  FIRST("首次发布"), REVISION("内容修改"), SESSION("新增场次");

  private final String label;

  ReviewKind(String label) {
    this.label = label;
  }

  /** 返回审核申请类型在页面中的中文标签. */
  public String getLabel() {
    return label;
  }
}
