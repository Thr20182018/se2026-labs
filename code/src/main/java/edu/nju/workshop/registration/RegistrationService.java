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
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RegistrationService {
  private final RegistrationRepository repository;
  private final RegistrationAdmission admission;
  private final OperationLogService logs;
  private final AppClock clock;
  private final TransactionTemplate transaction;

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

  public Map<Long, RegistrationResult> offers(long actorId, List<SessionRow> rows) {
    var existing = repository.active(actorId, rows.stream().map(SessionRow::id).toList());
    Map<Long, RegistrationResult> offers = new HashMap<>();
    for (SessionRow row : rows) {
      offers.put(row.id(), RegistrationResult.of("", existing.get(row.id()),
          row.remaining(), row.availability()));
    }
    return offers;
  }

  public List<RegistrationRow> mine(long actorId) {
    requireParticipant(actorId);
    return repository.mine(actorId);
  }

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
    Availability availability = !now().isBefore(session.deadline()) ? Availability.CLOSED
        : remaining <= 0 || repository.hasWaitlist(session.id()) ? Availability.WAITLIST
        : Availability.OPEN;
    return RegistrationResult.of(message, registration, remaining, availability);
  }

  private LocalDateTime now() {
    return BeijingTime.toLocal(clock.now());
  }
}
