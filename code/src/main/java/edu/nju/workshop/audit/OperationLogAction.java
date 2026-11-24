package edu.nju.workshop.audit;

/** 日志操作名称不包含完成时态，失败记录也使用同一名称。 */
public enum OperationLogAction {
  CREATE_ACTIVITY("创建活动"),
  UPDATE_ACTIVITY("修改活动"),
  CREATE_SESSION("创建场次"),
  UPDATE_SESSION("修改场次"),
  SUBMIT_REVIEW("提交活动审核"),
  APPROVE_REVIEW("通过活动审核"),
  REJECT_REVIEW("驳回活动审核"),
  REVIEW_ACTIVITY("审核活动"),
  PUBLISH_ACTIVITY("自动发布活动"),
  SUBMIT_REVISION("提交内容修改审核"),
  APPROVE_REVISION("通过内容修改审核"),
  REJECT_REVISION("驳回内容修改审核"),
  REVIEW_REVISION("审核内容修改"),
  SUBMIT_SESSION_REVIEW("提交场次审核"),
  APPROVE_SESSION_REVIEW("通过场次审核"),
  REJECT_SESSION_REVIEW("驳回场次审核"),
  REVIEW_SESSION("审核场次"),
  PUBLISH_SESSION("自动发布场次"),
  CANCEL_ACTIVITY("取消活动"),
  CANCEL_SESSION("取消场次"),
  UPDATE_CAPACITY("修改名额"),
  REGISTER("报名"),
  JOIN_WAITLIST("加入候补"),
  CANCEL_REGISTRATION("取消报名"),
  LEAVE_WAITLIST("退出候补"),
  PROMOTE_WAITLIST("自动递补"),
  EXPIRE_WAITLIST("结束候补"),
  CHECK_IN("签到"),
  MARK_ABSENT("标记缺席");

  private final String label;

  OperationLogAction(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public static String labelOf(String action) {
    try {
      return valueOf(action).label;
    } catch (IllegalArgumentException ex) {
      return action;
    }
  }
}
