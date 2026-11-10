package edu.nju.workshop.identity;

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
