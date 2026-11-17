package edu.nju.workshop.activity;

import edu.nju.workshop.audit.OperationLogService;
import edu.nju.workshop.registration.RegistrationService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PublishedActivityService {

  private final ActivityManagementService management;
  private final ActivityManagementRepository repository;
  private final OperationLogService logs;
  private final RegistrationService registrations;

  public PublishedActivityService(ActivityManagementService management,
      ActivityManagementRepository repository, OperationLogService logs,
      RegistrationService registrations) {
    this.management = management;
    this.repository = repository;
    this.logs = logs;
    this.registrations = registrations;
  }

  public String resizeCapacity(long actorId, long activityId, long sessionId, String capacity,
      Instant receivedAt) {
    return registrations.resize(actorId, activityId, sessionId, capacity, receivedAt);
  }

  public List<ReviewRequest> pendingReviews(long adminId) {
    management.requireRole(adminId, "ADMIN");
    return repository.pendingReviews();
  }

  public void submitRevision(long actorId, long id, ActivityDraft draft) {
    management.execute(actorId, id, null, "SUBMIT_REVISION", () -> {
      ManagedActivity activity = management.owned(actorId, id, true);
      requirePublished(activity);
      if (activity.revisionPending()) {
        throw new ActivityException(409, "已有内容修改待审核，请等待审核结果");
      }
      ActivityDraft valid = management.validate(draft);
      repository.submitRevision(id, valid);
      logs.success(actorId, id, null, "SUBMIT_REVISION",
          new ActivityDraft(activity.name(), activity.description()), valid);
      return null;
    });
  }

  public void reviewRevision(long adminId, long id, String decision, long reviewVersion) {
    String action = "approve".equals(decision) ? "APPROVE_REVISION"
        : "reject".equals(decision) ? "REJECT_REVISION" : "REVIEW_REVISION";
    management.execute(adminId, id, null, action, () -> {
      ManagedActivity activity = management.reviewable(adminId, id, true);
      requirePublished(activity);
      boolean approve = approve(decision);
      if (!activity.revisionPending()) {
        throw new ActivityException(409, "该内容修改已不在待审核状态，请刷新页面");
      }
      if (activity.reviewVersion() != reviewVersion) {
        throw new ActivityException(409, "审核申请已更新，请刷新页面后重试");
      }
      ActivityDraft pending = new ActivityDraft(
          activity.pendingName(), activity.pendingDescription());
      repository.decideRevision(id, approve);
      if (approve) {
        logs.success(adminId, id, null, action,
            new ActivityDraft(activity.name(), activity.description()), pending);
      } else {
        logs.success(adminId, id, null, action, pending, null);
      }
      return null;
    });
  }

  public void submitSession(long actorId, long id, long sessionId) {
    management.execute(actorId, id, sessionId, "SUBMIT_SESSION_REVIEW", () -> {
      ManagedActivity activity = management.owned(actorId, id, true);
      requirePublished(activity);
      ManagedSession session = session(activity, sessionId);
      management.requireEditable(session.status());
      management.validate(session);
      repository.submitSession(id, sessionId);
      logs.success(actorId, id, sessionId, "SUBMIT_SESSION_REVIEW",
          session.status(), PublicationStatus.PENDING);
      return null;
    });
  }

  public void reviewSession(long adminId, long id, long sessionId, String decision,
      long reviewVersion) {
    String action = "approve".equals(decision) ? "APPROVE_SESSION_REVIEW"
        : "reject".equals(decision) ? "REJECT_SESSION_REVIEW" : "REVIEW_SESSION";
    management.execute(adminId, id, sessionId, action, () -> {
      ManagedActivity activity = management.reviewable(adminId, id, true);
      requirePublished(activity);
      ManagedSession session = session(activity, sessionId);
      boolean approve = approve(decision);
      if (session.status() != PublicationStatus.PENDING) {
        throw new ActivityException(409, "该场次已不在待审核状态，请刷新页面");
      }
      if (session.reviewVersion() != reviewVersion) {
        throw new ActivityException(409, "审核申请已更新，请刷新页面后重试");
      }
      PublicationStatus next = approve ? PublicationStatus.PUBLISHED : PublicationStatus.REJECTED;
      repository.setSessionStatus(id, sessionId, next);
      logs.success(adminId, id, sessionId, action, session.status(), next);
      if (approve) {
        logs.success(null, id, sessionId, "PUBLISH_SESSION", session.status(), next);
      }
      return null;
    });
  }

  private void requirePublished(ManagedActivity activity) {
    if (!activity.published()) {
      throw new ActivityException(409, "当前活动未发布，不能执行此操作");
    }
  }

  private ManagedSession session(ManagedActivity activity, long sessionId) {
    return activity.sessions().stream().filter(row -> row.id() == sessionId).findFirst()
        .orElseThrow(() -> new ActivityException(403, "无权访问该场次"));
  }

  private boolean approve(String decision) {
    if (!"approve".equals(decision) && !"reject".equals(decision)) {
      throw new ActivityException(400, "请选择通过或驳回");
    }
    return "approve".equals(decision);
  }
}
