package edu.nju.workshop.identity;

/** 账号的单一业务角色及对应的中文展示名称. */
public enum Role {
  PARTICIPANT("参与者"),
  ORGANIZER("组织者"),
  ADMIN("管理员");

  private final String label;

  Role(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
