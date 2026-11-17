package edu.nju.workshop.attendance;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.activity.ActivityManagementService;
import edu.nju.workshop.activity.ManagedActivity;
import edu.nju.workshop.activity.ManagedSession;
import edu.nju.workshop.activity.PublicationStatus;
import edu.nju.workshop.audit.OperationLogService;
import edu.nju.workshop.registration.RegistrationStatus;
import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AttendanceService {
  private final AttendanceRepository repository;
  private final ActivityManagementService management;
  private final OperationLogService logs;
  private final AppClock clock;
  private final TransactionTemplate transaction;

  public AttendanceService(AttendanceRepository repository, ActivityManagementService management,
      OperationLogService logs, AppClock clock, PlatformTransactionManager transactions) {
    this.repository = repository;
    this.management = management;
    this.logs = logs;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactions);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(readOnly = true)
  public AttendancePage attendance(long actorId, long activityId, long sessionId) {
    ManagedActivity activity = management.ownedActivity(actorId, activityId);
    ManagedSession session = activity.sessions().stream().filter(item -> item.id() == sessionId)
        .findFirst().orElseThrow(() -> new ActivityException(403, "无权访问该场次"));
    return new AttendancePage(activity, session, repository.attendees(sessionId));
  }

  public String checkIn(long actorId, long activityId, long sessionId, long registrationId) {
    try {
      return transaction.execute(tx -> {
        var session = repository.lock(sessionId);
        // 场次锁串行化报名与取消；父活动使用已提交读取，不反向获取活动写锁。
        ManagedActivity activity = management.ownedActivity(actorId, activityId);
        if (session == null) {
          throw new ActivityException(404, "场次不存在");
        }
        if (session.activityId() != activityId) {
          throw new ActivityException(403, "无权访问该场次");
        }
        var registration = repository.registration(sessionId, registrationId);
        if (registration == null) {
          throw new ActivityException(404, "该场次不存在此报名记录");
        }
        if (registration.status() == RegistrationStatus.CHECKED_IN) {
          return "已签到";
        }
        LocalDateTime now = now();
        if (!activity.published() || session.status() != PublicationStatus.PUBLISHED) {
          throw new ActivityException(409, "活动或场次未发布或已取消，不能签到");
        }
        if (!now.isBefore(session.end())) {
          throw new ActivityException(409, "场次已结束，不能签到");
        }
        if (registration.status() != RegistrationStatus.REGISTERED) {
          throw new ActivityException(409, "只有报名成功的记录可以签到");
        }
        LocalDateTime checkedInAt = now.withNano(0);
        repository.checkIn(registrationId, checkedInAt);
        logs.success(actorId, activityId, sessionId, registrationId, "CHECK_IN", registration,
            new AttendanceRepository.Entry(registrationId, RegistrationStatus.CHECKED_IN,
                checkedInAt));
        return "签到成功";
      });
    } catch (RuntimeException ex) {
      logs.failure(actorId, activityId, sessionId, registrationId, "CHECK_IN",
          ex instanceof ActivityException ? ex.getMessage() : "操作未完成，事务已回滚");
      throw ex;
    }
  }

  public void markEndedAbsent() {
    for (long sessionId : repository.endedSessions(now())) {
      markAbsent(sessionId);
    }
  }

  private void markAbsent(long sessionId) {
    Long[] activityId = {null};
    Long[] registrationId = {null};
    try {
      transaction.executeWithoutResult(tx -> {
        var session = repository.lock(sessionId);
        if (session == null) {
          return;
        }
        activityId[0] = session.activityId();
        if (session.status() != PublicationStatus.PUBLISHED || now().isBefore(session.end())) {
          return;
        }
        // 整体取消会保留已经结束的已发布场次，缺席处理只检查场次自身状态。
        for (var registration : repository.registered(sessionId)) {
          registrationId[0] = registration.id();
          repository.markAbsent(registration.id());
          logs.success(null, session.activityId(), sessionId, registration.id(), "MARK_ABSENT",
              registration, new AttendanceRepository.Entry(registration.id(),
                  RegistrationStatus.ABSENT, registration.checkedInAt()));
        }
      });
    } catch (RuntimeException ex) {
      logs.failure(null, activityId[0], sessionId, registrationId[0], "MARK_ABSENT",
          "操作未完成，事务已回滚");
      throw ex;
    }
  }

  private LocalDateTime now() {
    return BeijingTime.toLocal(clock.now());
  }

  public record AttendancePage(ManagedActivity activity, ManagedSession session,
      List<AttendanceRow> attendees) {
  }
}
