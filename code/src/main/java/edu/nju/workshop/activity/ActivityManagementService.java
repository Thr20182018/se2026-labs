package edu.nju.workshop.activity;

import edu.nju.workshop.audit.OperationLogService;
import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ActivityManagementService {

  private final ActivityManagementRepository repository;
  private final OperationLogService logs;
  private final AppClock clock;
  private final TransactionTemplate transaction;

  public ActivityManagementService(ActivityManagementRepository repository,
      OperationLogService logs, AppClock clock, PlatformTransactionManager transactions) {
    this.repository = repository;
    this.logs = logs;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
  }

  public List<ActivitySummary> owned(long actorId) {
    requireRole(actorId, "ORGANIZER");
    return repository.owned(actorId);
  }

  public List<ActivitySummary> pending(long adminId) {
    requireRole(adminId, "ADMIN");
    return repository.pending();
  }

  @Transactional(readOnly = true)
  public ManagedActivity ownedActivity(long actorId, long id) {
    return owned(actorId, id, false);
  }

  @Transactional(readOnly = true)
  public ManagedActivity forReview(long adminId, long id) {
    return reviewable(adminId, id, false);
  }

  public long create(long actorId, ActivityDraft draft) {
    return execute(actorId, null, null, "CREATE_ACTIVITY", () -> {
      requireRole(actorId, "ORGANIZER");
      ActivityDraft valid = validate(draft);
      long id = repository.create(actorId, valid);
      logs.success(actorId, id, null, "CREATE_ACTIVITY", null, valid);
      return id;
    });
  }

  public void edit(long actorId, long id, ActivityDraft draft) {
    execute(actorId, id, null, "UPDATE_ACTIVITY", () -> {
      ManagedActivity activity = owned(actorId, id, true);
      requireEditable(activity);
      ActivityDraft valid = validate(draft);
      repository.edit(id, valid);
      logs.success(actorId, id, null, "UPDATE_ACTIVITY",
          new ActivityDraft(activity.name(), activity.description()), valid);
      return null;
    });
  }

  public long addSession(long actorId, long id, SessionDraft draft) {
    return execute(actorId, id, null, "CREATE_SESSION", () -> {
      ManagedActivity activity = owned(actorId, id, true);
      requireEditable(activity);
      ManagedSession valid = validate(0, draft, PublicationStatus.DRAFT);
      long sessionId = repository.addSession(id, valid);
      logs.success(actorId, id, sessionId, "CREATE_SESSION", null,
          new ManagedSession(sessionId, valid.startTime(), valid.endTime(), valid.deadline(),
              valid.location(), valid.capacity(), valid.status()));
      return sessionId;
    });
  }

  public void editSession(long actorId, long id, long sessionId, SessionDraft draft) {
    execute(actorId, id, sessionId, "UPDATE_SESSION", () -> {
      ManagedActivity activity = owned(actorId, id, true);
      requireEditable(activity);
      ManagedSession before = activity.sessions().stream().filter(row -> row.id() == sessionId)
          .findFirst().orElseThrow(() -> new ActivityException(403, "无权访问该场次"));
      if (!before.endTime().isAfter(BeijingTime.toLocal(clock.now()))) {
        throw new ActivityException(409, "已结束的场次不能修改");
      }
      requireEditable(before.status());
      ManagedSession valid = validate(sessionId, draft, before.status());
      repository.editSession(id, valid);
      logs.success(actorId, id, sessionId, "UPDATE_SESSION", before, valid);
      return null;
    });
  }

  public void submit(long actorId, long id) {
    execute(actorId, id, null, "SUBMIT_REVIEW", () -> {
      ManagedActivity activity = owned(actorId, id, true);
      requireEditable(activity);
      validate(new ActivityDraft(activity.name(), activity.description()));
      if (activity.sessions().isEmpty()) {
        throw new ActivityException(400, "请至少添加一个场次后再提交审核");
      }
      for (ManagedSession session : activity.sessions()) {
        requireEditable(session.status());
        validate(session);
      }
      repository.submitForReview(id);
      logs.success(actorId, id, null, "SUBMIT_REVIEW", activity.status(),
          PublicationStatus.PENDING);
      return null;
    });
  }

  public void review(long adminId, long id, String decision, long reviewVersion) {
    String action = "approve".equals(decision) ? "APPROVE_REVIEW"
        : "reject".equals(decision) ? "REJECT_REVIEW" : "REVIEW_ACTIVITY";
    execute(adminId, id, null, action, () -> {
      ManagedActivity activity = reviewable(adminId, id, true);
      if (!"approve".equals(decision) && !"reject".equals(decision)) {
        throw new ActivityException(400, "请选择通过或驳回");
      }
      if (activity.status() != PublicationStatus.PENDING) {
        throw new ActivityException(409, "该活动已不在待审核状态，请刷新页面");
      }
      // 驳回重提会再次进入 PENDING，必须核对这一轮申请，不能只检查状态。
      if (activity.reviewVersion() != reviewVersion) {
        throw new ActivityException(409, "审核申请已更新，请刷新页面后重试");
      }
      if (activity.sessions().isEmpty() || activity.sessions().stream()
          .anyMatch(session -> session.status() != PublicationStatus.PENDING)) {
        throw new ActivityException(409, "场次审核状态已变化，请刷新页面");
      }
      PublicationStatus next = "approve".equals(decision)
          ? PublicationStatus.PUBLISHED : PublicationStatus.REJECTED;
      repository.setStatus(id, next);
      logs.success(adminId, id, null, action, activity.status(), next);
      if (next == PublicationStatus.PUBLISHED) {
        logs.success(null, id, null, "PUBLISH_ACTIVITY", activity.status(), next);
      }
      return null;
    });
  }

  private ManagedActivity owned(long actorId, long id, boolean lock) {
    requireRole(actorId, "ORGANIZER");
    ManagedActivity activity = repository.find(id, lock)
        .orElseThrow(() -> new ActivityException(403, "无权访问该活动"));
    if (activity.organizerId() != actorId) {
      throw new ActivityException(403, "无权访问该活动");
    }
    return activity;
  }

  private ManagedActivity reviewable(long adminId, long id, boolean lock) {
    requireRole(adminId, "ADMIN");
    ManagedActivity activity = repository.find(id, lock)
        .orElseThrow(() -> new ActivityException(404, "活动不存在"));
    if (activity.organizerId() == adminId) {
      throw new ActivityException(403, "不能审核自己负责的活动");
    }
    return activity;
  }

  private void requireRole(long actorId, String role) {
    if (!role.equals(repository.role(actorId))) {
      throw new ActivityException(403, "无权执行该操作");
    }
  }

  private void requireEditable(ManagedActivity activity) {
    requireEditable(activity.status());
  }

  private void requireEditable(PublicationStatus status) {
    if (status != PublicationStatus.DRAFT && status != PublicationStatus.REJECTED) {
      throw new ActivityException(409, "当前状态不能编辑或重新提交，请刷新页面");
    }
  }

  private ActivityDraft validate(ActivityDraft draft) {
    return new ActivityDraft(text(draft.name(), "活动名称", 200),
        text(draft.description(), "活动简介", 10000));
  }

  private ManagedSession validate(long id, SessionDraft draft, PublicationStatus status) {
    int capacity;
    try {
      capacity = Integer.parseInt(draft.capacity() == null ? "" : draft.capacity().strip());
    } catch (NumberFormatException ex) {
      throw new ActivityException(400, "名额必须是非负整数");
    }
    ManagedSession session = new ManagedSession(id, time(draft.startTime()), time(draft.endTime()),
        time(draft.deadline()), text(draft.location(), "场次地点", 200), capacity, status);
    validate(session);
    return session;
  }

  private void validate(ManagedSession session) {
    if (session.capacity() < 0) {
      throw new ActivityException(400, "名额必须是非负整数");
    }
    if (session.deadline().isAfter(session.startTime())
        || !session.startTime().isBefore(session.endTime())) {
      throw new ActivityException(400, "时间必须满足：报名截止时间 ≤ 开始时间 < 结束时间");
    }
    text(session.location(), "场次地点", 200);
  }

  private LocalDateTime time(String raw) {
    try {
      LocalDateTime value = LocalDateTime.parse(raw == null ? "" : raw.strip()).withNano(0);
      if (value.getYear() < 1000 || value.getYear() > 9999) {
        throw new ActivityException(400, "日期年份应在 1000 至 9999 之间");
      }
      return value;
    } catch (DateTimeParseException ex) {
      throw new ActivityException(400, "请填写有效的开始、结束和报名截止时间");
    }
  }

  private String text(String raw, String field, int limit) {
    String value = raw == null ? "" : raw.strip();
    if (value.isBlank() || value.codePointCount(0, value.length()) > limit) {
      throw new ActivityException(400, field + "须填写 1 至 " + limit + " 个字符");
    }
    return value;
  }

  private <T> T execute(long actorId, Long activityId, Long sessionId, String action,
      Supplier<T> operation) {
    try {
      // 所有写入口先锁活动，再按编号锁场次，读取最新状态后一起提交业务和日志。
      return transaction.execute(status -> operation.get());
    } catch (RuntimeException ex) {
      String reason = ex instanceof ActivityException ? ex.getMessage() : "操作未完成，事务已回滚";
      logs.failure(actorId, activityId, sessionId, action, reason);
      throw ex;
    }
  }
}
