package edu.nju.workshop.audit;

import java.time.LocalDateTime;

/** 日志查询展示数据，身份只包含姓名和账号。 */
public record OperationLogEntry(long id, LocalDateTime operatedAt, String actorType,
    String actorName, String actorAccount, Long activityId, String activityName,
    Long sessionId, LocalDateTime sessionStart, Long registrationId, String action,
    String result, String failureReason, String beforeValue, String afterValue) {

  public String actionLabel() {
    return OperationLogAction.labelOf(action);
  }

  public boolean success() {
    return "SUCCESS".equals(result);
  }

  public String actorLabel() {
    return "SYSTEM".equals(actorType) ? "系统" : actorName;
  }

  OperationLogEntry withValues(String before, String after) {
    return new OperationLogEntry(id, operatedAt, actorType, actorName, actorAccount,
        activityId, activityName, sessionId, sessionStart, registrationId, action,
        result, failureReason, before, after);
  }
}
