package edu.nju.workshop.statistics;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.shared.ManagementScope;
import edu.nju.workshop.shared.ManagementScopeService;
import edu.nju.workshop.shared.ManagementScopeService.Choice;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 按管理权限组织一致快照中的名单、人数统计和完整 CSV 导出. */
@Service
public class StatisticsService {
  public static final int PAGE_SIZE = 20;
  private static final DateTimeFormatter CSV_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final StatisticsRepository repository;
  private final ManagementScopeService scopes;

  /** 使用统计仓库和管理范围服务读取已授权的数据. */
  public StatisticsService(StatisticsRepository repository, ManagementScopeService scopes) {
    this.repository = repository;
    this.scopes = scopes;
  }

  /** 通过 MySQL 一致性读取，在同一已提交快照中查询授权范围内的名单、总数、容量及状态统计. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public StatisticsView search(AccountUser user, Long activityId, Long sessionId,
      int requestedPage) {
    if (requestedPage < 1) {
      throw new ActivityException(400, "页码必须大于零");
    }
    ManagementScope scope = scopes.resolve(user, activityId, sessionId);
    long total = repository.count(scope);
    long pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    int page = (int) Math.min(requestedPage, pages);
    var rows = repository.rows(scope, PAGE_SIZE, (page - 1L) * PAGE_SIZE);
    var pageData = new StatisticsPage(rows, total, page, pages, repository.summary(scope));
    Long attendanceActivityId = sessionId == null ? null : repository.sessions(scope).getFirst()
        .activityId();
    return new StatisticsView(scope, scopes.activities(scope), scopes.sessions(scope), pageData,
        attendanceActivityId);
  }

  /** 导出授权筛选范围内的完整报名名单，不受当前分页限制. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] registrationsCsv(AccountUser user, Long activityId, Long sessionId) {
    ManagementScope scope = scopes.resolve(user, activityId, sessionId);
    CsvWriter csv = new CsvWriter();
    csv.row("报名编号", "参与者", "账号", "活动编号", "活动", "场次编号", "开始时间", "结束时间",
        "组织者", "组织者账号", "报名时间", "当前状态", "签到时间");
    for (var row : repository.rows(scope, null, 0)) {
      csv.row(row.id(), row.participantName(), row.account(), row.activityId(), row.activityName(),
          row.sessionId(), time(row.startTime()), time(row.endTime()), row.organizerName(),
          row.organizerAccount(), time(row.registeredAt()), row.status().getLabel(),
          time(row.checkedInAt()));
    }
    return csv.bytes();
  }

  /** 导出授权筛选范围内的逐场次人数和总计. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] summaryCsv(AccountUser user, Long activityId, Long sessionId) {
    ManagementScope scope = scopes.resolve(user, activityId, sessionId);
    CsvWriter csv = new CsvWriter();
    csv.row("活动编号", "活动", "场次编号", "开始时间", "结束时间", "组织者", "容量", "报名人数",
        "到场人数", "缺席人数", "剩余名额");
    for (var row : repository.sessions(scope)) {
      var summary = row.summary();
      csv.row(row.activityId(), row.activityName(), row.sessionId(), time(row.startTime()),
          time(row.endTime()), row.organizerName(), row.capacity(), summary.registered(),
          summary.attended(), summary.absent(), summary.remaining());
    }
    var total = repository.summary(scope);
    csv.row("", "合计", "", "", "", "", total.registered() + total.remaining(),
        total.registered(), total.attended(), total.absent(), total.remaining());
    return csv.bytes();
  }

  private String time(LocalDateTime value) {
    return value == null ? "" : CSV_TIME.format(value);
  }

  /** 统计页面所需的授权范围、筛选选项、名单及签到入口. */
  public record StatisticsView(ManagementScope scope, List<Choice> activities,
      List<Choice> sessions, StatisticsPage pageData, Long attendanceActivityId) {
  }
}
