package edu.nju.workshop.registration;

import edu.nju.workshop.activity.Availability;
import edu.nju.workshop.activity.SessionRow;
import edu.nju.workshop.audit.OperationLogService;
import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 在场次锁保护下处理报名、候补、取消递补和容量调整. */
@Service
public class RegistrationService {
  private final RegistrationRepository repository;
  private final RegistrationAdmission admission;
  private final OperationLogService logs;
  private final AppClock clock;
  private final TransactionTemplate transaction;

  /** 配置报名依赖，并使锁后查询读取前一事务已提交的状态. */
  public RegistrationService(RegistrationRepository repository, RegistrationAdmission admission,
      OperationLogService logs, AppClock clock, PlatformTransactionManager transactions) {
    this.repository = repository;
    this.admission = admission;
    this.logs = logs;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
    // 等待场次锁后，人数和已有报名必须读取前一事务刚提交的结果。
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 按请求接收时间判断截止资格，并在场次事务内创建报名或候补. */
  public RegistrationResult apply(long actorId, long sessionId, String action, Instant receivedAt) {
    Long[] activityId = {null};
    String logAction = "waitlist".equals(action) ? "JOIN_WAITLIST" : "REGISTER";
    try {
      return transaction.execute(tx -> {
        requireParticipant(actorId);
        var session = repository.lock(sessionId);
        if (session == null) {
          throw failure(404, "场次不存在", null, null);
        }
        activityId[0] = session.activityId();
        if ("CANCELLED".equals(session.status())) {
          throw new RegistrationException(409, RegistrationResult.of("场次已取消", null,
              session.capacity() - repository.registered(sessionId), Availability.CANCELLED));
        }
        if (!"PUBLISHED".equals(session.status())
            || !repository.activityPublished(session.activityId())) {
          throw failure(404, "该场次当前不可报名", null, null);
        }
        Registration existing = repository.active(actorId, sessionId);
        if (existing != null) {
          return result("已有申请：" + existing.status().getLabel(), existing, session);
        }
        if (!"register".equals(action) && !"waitlist".equals(action)) {
          throw failure(400, "请选择报名或加入候补", null, session);
        }
        if (!BeijingTime.toLocal(receivedAt).isBefore(session.deadline())) {
          throw failure(409, "报名已截止", null, session);
        }
        if (!now().isBefore(session.end())) {
          throw failure(409, "场次已结束", null, session);
        }
        boolean full = repository.registered(sessionId) >= session.capacity()
            || repository.hasWaitlist(sessionId);
        boolean waitlist = "waitlist".equals(action);
        if (!waitlist && full) {
          throw failure(409, "名额已满或已有候补者，请选择加入候补", null, session);
        }
        if (waitlist && !full) {
          throw failure(409, "还有名额，请直接报名", null, session);
        }
        Registration registration = repository.insert(actorId, session, waitlist,
            BeijingTime.toLocal(receivedAt));
        logs.success(actorId, session.activityId(), sessionId, registration.id(), logAction,
            null, registration);
        return result(waitlist ? "已加入候补，可在“我的报名”查看结果。" : "报名成功！",
            registration, session);
      });
    } catch (RuntimeException ex) {
      logs.failure(actorId, activityId[0], sessionId, logAction,
          ex instanceof RegistrationException ? ex.getMessage() : "操作未完成，事务已回滚");
      throw ex;
    }
  }

  /** 合并参与者已有申请与场次状态，生成列表中的操作按钮数据. */
  public Map<Long, RegistrationResult> offers(long actorId, List<SessionRow> rows) {
    var existing = repository.active(actorId, rows.stream().map(SessionRow::id).toList());
    Map<Long, RegistrationResult> offers = new HashMap<>();
    for (SessionRow row : rows) {
      offers.put(row.id(), RegistrationResult.of("", existing.get(row.id()),
          row.remaining(), row.availability()));
    }
    return offers;
  }

  /** 校验参与者身份后读取其全部报名记录. */
  public List<RegistrationRow> mine(long actorId) {
    requireParticipant(actorId);
    return repository.mine(actorId);
  }

  /** 取消本人的报名或候补，并在同一事务内递补释放的名额. */
  public RegistrationResult cancel(long actorId, long sessionId, long registrationId,
      Instant receivedAt) {
    Long[] activityId = {null};
    String[] action = {"CANCEL_REGISTRATION"};
    try {
      return transaction.execute(tx -> {
        requireParticipant(actorId);
        var session = requireSession(sessionId);
        activityId[0] = session.activityId();
        Registration before = repository.owned(actorId, sessionId, registrationId);
        if (before == null) {
          throw failure(403, "无权操作该报名记录", null, null);
        }
        if (before.status() == RegistrationStatus.CANCELLED) {
          return result("申请已取消", before, session);
        }
        boolean waiting = before.status() == RegistrationStatus.WAITLIST;
        action[0] = waiting ? "LEAVE_WAITLIST" : "CANCEL_REGISTRATION";
        if (!waiting && before.status() != RegistrationStatus.REGISTERED) {
          throw failure(409, "当前报名状态不能取消", before, session);
        }
        requireAccepting(session, receivedAt);
        repository.setStatus(registrationId, RegistrationStatus.CANCELLED);
        logs.success(actorId, session.activityId(), sessionId, registrationId, action[0],
            before.status(), RegistrationStatus.CANCELLED);
        int promoted = waiting ? 0
            : promote(session, Math.min(1, session.capacity() - repository.registered(sessionId)));
        String message = waiting ? "已退出候补。" : promoted == 0 ? "报名已取消。" : "报名已取消，候补已递补。";
        return result(message, new Registration(registrationId, RegistrationStatus.CANCELLED,
            before.waitlistOrder()), session);
      });
    } catch (RuntimeException ex) {
      logs.failure(actorId, activityId[0], sessionId, registrationId, action[0], reason(ex));
      throw ex;
    }
  }

  /** 校验组织者归属及截止资格后调整容量，并递补新增名额. */
  public String resize(long actorId, long activityId, long sessionId, String rawCapacity,
      Instant receivedAt) {
    try {
      return transaction.execute(tx -> {
        if (!repository.organizerOwns(actorId, activityId)) {
          throw failure(403, "无权修改该活动的容量", null, null);
        }
        var session = requireSession(sessionId);
        if (session.activityId() != activityId) {
          throw failure(403, "场次不属于该活动", null, null);
        }
        requireAccepting(session, receivedAt);
        int capacity;
        try {
          capacity = Integer.parseInt(rawCapacity.strip());
        } catch (NumberFormatException ex) {
          throw failure(400, "名额必须是非负整数", null, session);
        }
        if (capacity < 0) {
          throw failure(400, "名额必须是非负整数", null, session);
        }
        int occupied = repository.registered(sessionId);
        if (capacity < occupied) {
          throw failure(409, "容量不能小于当前报名人数 " + occupied + " 人", null, session);
        }
        repository.resize(sessionId, capacity);
        int promoted = promote(session,
            Math.min(capacity - session.capacity(), capacity - occupied));
        logs.success(actorId, activityId, sessionId, "UPDATE_CAPACITY",
            session.capacity(), capacity);
        return "容量已更新，已递补 " + promoted + " 人。";
      });
    } catch (RuntimeException ex) {
      logs.failure(actorId, activityId, sessionId, "UPDATE_CAPACITY", reason(ex));
      throw ex;
    }
  }

  /** 活动取消事务已锁定场次，关联记录与场次状态一起提交. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void cancelForSession(long actorId, long activityId, long sessionId) {
    for (Registration registration : repository.cancellable(sessionId)) {
      repository.setStatus(registration.id(), RegistrationStatus.CANCELLED);
      logs.success(actorId, activityId, sessionId, registration.id(), "CANCEL_REGISTRATION",
          registration.status(), RegistrationStatus.CANCELLED);
    }
  }

  private int promote(RegistrationRepository.Session session, int slots) {
    if (slots <= 0) {
      return 0;
    }
    int promoted = 0;
    for (Registration registration : repository.waitlist(session.id())) {
      if (promoted == slots) {
        break;
      }
      repository.setStatus(registration.id(), RegistrationStatus.REGISTERED);
      logs.success(null, session.activityId(), session.id(), registration.id(), "PROMOTE_WAITLIST",
          RegistrationStatus.WAITLIST, RegistrationStatus.REGISTERED);
      promoted++;
    }
    return promoted;
  }

  private RegistrationRepository.Session requireSession(long sessionId) {
    var session = repository.lock(sessionId);
    if (session == null) {
      throw failure(404, "场次不存在", null, null);
    }
    return session;
  }

  private void requireAccepting(RegistrationRepository.Session session, Instant receivedAt) {
    if (!"PUBLISHED".equals(session.status())
        || !repository.activityPublished(session.activityId())) {
      throw failure(409, "场次尚未发布或已取消", null, session);
    }
    if (!now().isBefore(session.end())) {
      throw failure(409, "场次已结束", null, session);
    }
    if (!BeijingTime.toLocal(receivedAt).isBefore(session.deadline())) {
      throw failure(409, "报名已截止，不能取消、退出候补或修改容量", null, session);
    }
  }

  private String reason(RuntimeException ex) {
    return ex instanceof RegistrationException ? ex.getMessage() : "操作未完成，事务已回滚";
  }

  /** 等待截止前的在途请求完成后，将剩余候补标为候补未成功. */
  public void closeDueWaitlists() {
    for (long sessionId : repository.dueSessions(now())) {
      transaction.executeWithoutResult(tx -> {
        var session = repository.lock(sessionId);
        if (session == null || !"PUBLISHED".equals(session.status())
            || now().isBefore(session.deadline())) {
          return;
        }
        // 只等截止前收到的申请；晚到请求的重试不应延迟候补关闭。
        if (admission.hasPendingBefore(sessionId,
            session.deadline().atZone(BeijingTime.ZONE).toInstant())) {
          return;
        }
        for (Registration registration : repository.waitlist(sessionId)) {
          repository.expire(registration.id());
          logs.success(null, session.activityId(), sessionId, registration.id(), "EXPIRE_WAITLIST",
              registration.status(), RegistrationStatus.WAITLIST_EXPIRED);
        }
      });
    }
  }

  private void requireParticipant(long actorId) {
    if (!repository.participant(actorId)) {
      throw failure(403, "仅参与者可以报名或查看本人报名", null, null);
    }
  }

  private RegistrationException failure(int status, String message, Registration registration,
      RegistrationRepository.Session session) {
    return new RegistrationException(status, session == null
        ? RegistrationResult.of(message, registration, 0, Availability.CLOSED)
        : result(message, registration, session));
  }

  private RegistrationResult result(String message, Registration registration,
      RegistrationRepository.Session session) {
    int remaining = session.capacity() - repository.registered(session.id());
    Availability availability = "CANCELLED".equals(session.status()) ? Availability.CANCELLED
        : !now().isBefore(session.deadline()) ? Availability.CLOSED
        : remaining <= 0 || repository.hasWaitlist(session.id()) ? Availability.WAITLIST
        : Availability.OPEN;
    return RegistrationResult.of(message, registration, remaining, availability);
  }

  private LocalDateTime now() {
    return BeijingTime.toLocal(clock.now());
  }
}
