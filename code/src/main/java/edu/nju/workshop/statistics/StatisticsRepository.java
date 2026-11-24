package edu.nju.workshop.statistics;

import edu.nju.workshop.registration.RegistrationStatus;
import edu.nju.workshop.shared.ManagementScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 按授权范围读取报名名单、分页数量和统一口径的场次统计. */
@Repository
public class StatisticsRepository {
  private static final String REGISTRATION_FROM = """
      FROM registrations r JOIN sessions s ON s.id = r.session_id
        JOIN activities a ON a.id = s.activity_id
        JOIN users u ON u.id = r.participant_id JOIN users o ON o.id = a.organizer_id
      """;

  private static final String SESSION_COUNTS = """
      SELECT a.id AS activity_id, a.name AS activity_name, s.id AS session_id,
        s.start_time, s.end_time, o.display_name AS organizer_name, s.capacity,
        SUM(CASE WHEN r.status IN ('REGISTERED', 'CHECKED_IN', 'ABSENT')
          THEN 1 ELSE 0 END) AS registered,
        SUM(CASE WHEN r.status = 'CHECKED_IN' THEN 1 ELSE 0 END) AS attended,
        SUM(CASE WHEN r.status = 'ABSENT' THEN 1 ELSE 0 END) AS absent
      FROM sessions s JOIN activities a ON a.id = s.activity_id
        JOIN users o ON o.id = a.organizer_id
        LEFT JOIN registrations r ON r.session_id = s.id
      """;

  private static final String SESSION_GROUP = """
       GROUP BY a.id, a.name, s.id, s.start_time, s.end_time, o.display_name, s.capacity
      """;

  private final JdbcTemplate jdbc;

  /** 使用数据库模板查询报名及场次统计数据. */
  public StatisticsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long count(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.queryForObject("SELECT COUNT(*) " + REGISTRATION_FROM + scope.where(args),
        Long.class, args.toArray());
  }

  List<StatisticsRow> rows(ManagementScope scope, Integer limit, long offset) {
    List<Object> args = new ArrayList<>();
    String sql = """
        SELECT r.id, u.display_name AS participant_name, u.account,
          a.id AS activity_id, a.name AS activity_name, s.id AS session_id,
          s.start_time, s.end_time, o.display_name AS organizer_name,
          o.account AS organizer_account, r.registered_at, r.status, r.checked_in_at
        """ + REGISTRATION_FROM + scope.where(args) + " ORDER BY r.registered_at DESC, r.id DESC";
    if (limit != null) {
      sql += " LIMIT ? OFFSET ?";
      args.add(limit);
      args.add(offset);
    }
    return jdbc.query(sql, (rs, row) -> new StatisticsRow(rs.getLong("id"),
        rs.getString("participant_name"), rs.getString("account"), rs.getLong("activity_id"),
        rs.getString("activity_name"), rs.getLong("session_id"),
        rs.getObject("start_time", LocalDateTime.class),
        rs.getObject("end_time", LocalDateTime.class), rs.getString("organizer_name"),
        rs.getString("organizer_account"), rs.getObject("registered_at", LocalDateTime.class),
        RegistrationStatus.valueOf(rs.getString("status")),
        rs.getObject("checked_in_at", LocalDateTime.class)), args.toArray());
  }

  StatisticsSummary summary(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    String sql = """
        SELECT COALESCE(SUM(registered), 0) AS registered,
          COALESCE(SUM(attended), 0) AS attended, COALESCE(SUM(absent), 0) AS absent,
          COALESCE(SUM(capacity - registered), 0) AS remaining FROM (
        """ + SESSION_COUNTS + scope.where(args) + SESSION_GROUP + ") totals";
    return jdbc.queryForObject(sql, (rs, row) -> summary(rs), args.toArray());
  }

  private StatisticsSummary summary(ResultSet rs) throws SQLException {
    return new StatisticsSummary(rs.getLong("registered"), rs.getLong("attended"),
        rs.getLong("absent"), rs.getLong("remaining"));
  }

  List<SessionStatistics> sessions(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.query("SELECT counts.*, capacity - registered AS remaining FROM ("
        + SESSION_COUNTS + scope.where(args) + SESSION_GROUP
        + ") counts ORDER BY start_time DESC, session_id DESC", (rs, row) ->
            new SessionStatistics(rs.getLong("activity_id"), rs.getString("activity_name"),
                rs.getLong("session_id"), rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class), rs.getString("organizer_name"),
                rs.getLong("capacity"), summary(rs)), args.toArray());
  }
}
