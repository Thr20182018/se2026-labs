package edu.nju.workshop.activity;

import edu.nju.workshop.audit.OperationLogService;
import edu.nju.workshop.registration.RegistrationService;
import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 在活动及场次锁内联动取消目标、报名记录和操作日志. */
@Service
public class ActivityLifecycleService {
  private final ActivityManagementService management;
  private final ActivityManagementRepository activities;
  private final ActivityLifecycleRepository repository;
  private final RegistrationService registrations;
  private final OperationLogService logs;
  private final AppClock clock;
  private final TransactionTemplate transaction;

  /** 组合权限、取消和报名服务，并以读已提交事务执行取消联动. */
  public ActivityLifecycleService(ActivityManagementService management,
      ActivityManagementRepository activities, ActivityLifecycleRepository repository,
      RegistrationService registrations, OperationLogService logs, AppClock clock,
      PlatformTransactionManager transactions) {
    this.management = management;
    this.activities = activities;
    this.repository = repository;
    this.registrations = registrations;
    this.logs = logs;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 校验管理员身份后读取全平台活动概要. */
  public List<ActivitySummary> all(long adminId) {
    management.requireRole(adminId, "ADMIN");
    return activities.all();
  }

  /** 校验管理员身份后读取活动及其场次详情. */
  public ManagedActivity forAdmin(long adminId, long activityId) {
    return management.forReview(adminId, activityId);
  }

  /** 取消活动及尚未结束的场次，保留已结束场次和记录. */
  public String cancelActivity(long actorId, long activityId) {
    return execute(actorId, activityId, null, "CANCEL_ACTIVITY", () -> {
      ManagedActivity activity = lock(actorId, activityId);
      if (activity.status() == PublicationStatus.CANCELLED) {
        return "活动已取消";
      }
      LocalDateTime now = BeijingTime.toLocal(clock.now());
      if (activity.sessions().stream().noneMatch(session -> now.isBefore(session.endTime()))) {
        throw new ActivityException(409, "活动没有尚未结束的场次，不能取消");
      }
      for (ManagedSession session : activity.sessions()) {
        if (now.isBefore(session.endTime()) && session.status() != PublicationStatus.CANCELLED) {
          cancel(actorId, activityId, session);
        }
      }
      repository.cancelActivity(activityId);
      logs.success(actorId, activityId, null, "CANCEL_ACTIVITY", activity.status(),
          PublicationStatus.CANCELLED);
      return "活动已取消，已结束场次及其记录已保留";
    });
  }

  /** 取消尚未结束的指定场次，并在同一事务中取消相关有效报名. */
  public String cancelSession(long actorId, long activityId, long sessionId) {
    return execute(actorId, activityId, sessionId, "CANCEL_SESSION", () -> {
      ManagedActivity activity = lock(actorId, activityId);
      ManagedSession session = activity.sessions().stream().filter(item -> item.id() == sessionId)
          .findFirst().orElseThrow(() -> new ActivityException(403, "无权访问该场次"));
      if (session.status() == PublicationStatus.CANCELLED) {
        return "场次已取消";
      }
      if (!BeijingTime.toLocal(clock.now()).isBefore(session.endTime())) {
        throw new ActivityException(409, "已结束的场次不能取消");
      }
      cancel(actorId, activityId, session);
      return "场次已取消";
    });
  }

  private ManagedActivity lock(long actorId, long activityId) {
    // 管理查询先锁活动，再按编号升序锁全部场次；报名联动复用这些场次锁。
    return "ADMIN".equals(activities.role(actorId))
        ? management.reviewable(actorId, activityId, true)
        : management.owned(actorId, activityId, true);
  }

  private void cancel(long actorId, long activityId, ManagedSession session) {
    registrations.cancelForSession(actorId, activityId, session.id());
    repository.cancelSession(activityId, session.id());
    logs.success(actorId, activityId, session.id(), "CANCEL_SESSION", session.status(),
        PublicationStatus.CANCELLED);
  }

  private String execute(long actorId, long activityId, Long sessionId, String action,
      Supplier<String> operation) {
    try {
      return transaction.execute(status -> operation.get());
    } catch (RuntimeException ex) {
      logs.failure(actorId, activityId, sessionId, action,
          ex instanceof ActivityException ? ex.getMessage() : "操作未完成，事务已回滚");
      throw ex;
    }
  }
}
