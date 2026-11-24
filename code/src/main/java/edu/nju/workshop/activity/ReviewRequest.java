package edu.nju.workshop.activity;

/** 审核队列的一条申请，关联活动以及可选的新增场次. */
public record ReviewRequest(
    long activityId, Long sessionId, String name, String organizerName, ReviewKind kind) {

  /** 按申请类型生成活动、内容修改或新增场次的审核详情地址. */
  public String reviewUrl() {
    String base = "/admin/reviews/" + activityId;
    return switch (kind) {
      case FIRST -> base;
      case REVISION -> base + "/revision";
      case SESSION -> base + "/sessions/" + sessionId;
    };
  }
}
