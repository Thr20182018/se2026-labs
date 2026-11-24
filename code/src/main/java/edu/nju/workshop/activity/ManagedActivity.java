package edu.nju.workshop.activity;

import java.time.LocalDateTime;
import java.util.List;

/** 活动管理详情，包含当前内容、待审内容、审核版本及所属场次. */
public record ManagedActivity(
    long id, String name, String description, long organizerId, String organizerName,
    PublicationStatus status, long reviewVersion, String pendingName, String pendingDescription,
    boolean revisionPending, PublicationStatus revisionStatus, List<ManagedSession> sessions) {

  /** 判断活动是否处于可直接编辑并重新提交的草稿或驳回状态. */
  public boolean editable() {
    return status == PublicationStatus.DRAFT || status == PublicationStatus.REJECTED;
  }

  /** 判断活动是否已通过审核并正式发布. */
  public boolean published() {
    return status == PublicationStatus.PUBLISHED;
  }

  /** 判断活动尚未取消且仍有未结束场次，供页面决定是否显示取消入口. */
  public boolean cancellable(LocalDateTime now) {
    return status != PublicationStatus.CANCELLED
        && sessions.stream().anyMatch(session -> session.endTime().isAfter(now));
  }
}
