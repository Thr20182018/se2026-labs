package edu.nju.workshop.registration;

/** 报名和候补记录的业务状态及中文展示名称. */
public enum RegistrationStatus {
  REGISTERED("报名成功"), WAITLIST("候补中"), CANCELLED("已取消"),
  CHECKED_IN("已签到"), ABSENT("缺席"), WAITLIST_EXPIRED("候补未成功");

  private final String label;

  RegistrationStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
