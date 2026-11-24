package edu.nju.workshop.activity;

/** 管理列表中的活动概要及内容修改、新增场次的审核状态汇总. */
public record ActivitySummary(
    long id, String name, PublicationStatus status, int sessionCount, String organizerName,
    PublicationStatus revisionStatus, int newSessionsPending, int newSessionsRejected,
    int newSessionsPublished) {

  /** 判断是否存在需要在列表展示的新增场次审核记录. */
  public boolean hasNewSessionReviews() {
    return newSessionsPending + newSessionsRejected + newSessionsPublished > 0;
  }
}
