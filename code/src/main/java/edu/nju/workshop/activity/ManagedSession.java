package edu.nju.workshop.activity;

import java.time.LocalDateTime;

/** 场次管理详情，保存时间、地点、容量及独立的发布状态和审核版本. */
public record ManagedSession(
    long id, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime deadline,
    String location, int capacity, PublicationStatus status, long reviewVersion) {

  /** 判断场次是否处于允许编辑的草稿或驳回状态. */
  public boolean editable() {
    return status == PublicationStatus.DRAFT || status == PublicationStatus.REJECTED;
  }
}
