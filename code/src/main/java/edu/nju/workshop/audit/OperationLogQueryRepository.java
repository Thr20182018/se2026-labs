package edu.nju.workshop.audit;

import edu.nju.workshop.shared.ManagementScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationLogQueryRepository {

  private static final String FROM = """
      FROM operation_logs l
      LEFT JOIN sessions s ON s.id = l.session_id
        AND (l.activity_id IS NULL OR s.activity_id = l.activity_id)
      LEFT JOIN activities a ON a.id = COALESCE(l.activity_id, s.activity_id)
      LEFT JOIN users u ON u.id = l.actor_id
      """;
  private static final String COLUMNS = """
      SELECT l.*, u.display_name AS actor_name, u.account AS actor_account,
        COALESCE(l.activity_id, s.activity_id) AS target_activity_id,
        a.name AS activity_name, s.start_time AS session_start
      """;
  private final JdbcTemplate jdbc;

  public OperationLogQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long count(ManagementScope scope, LocalDateTime from, LocalDateTime to, String action) {
    List<Object> args = new ArrayList<>();
    String where = where(scope, from, to, action, args);
    return jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
  }

  List<OperationLogEntry> rows(ManagementScope scope, LocalDateTime from, LocalDateTime to,
      String action, int page, int size) {
    List<Object> args = new ArrayList<>();
    String where = where(scope, from, to, action, args);
    args.add(size);
    args.add((long) (page - 1) * size);
    return jdbc.query(COLUMNS + FROM + where
        + " ORDER BY l.operated_at DESC, l.id DESC LIMIT ? OFFSET ?",
        this::map, args.toArray());
  }

  Optional<OperationLogEntry> selected(ManagementScope scope, LocalDateTime from,
      LocalDateTime to, String action, long id) {
    List<Object> args = new ArrayList<>();
    String where = where(scope, from, to, action, args);
    args.add(id);
    return jdbc.query(COLUMNS + FROM + where + " AND l.id = ?", this::map, args.toArray())
        .stream().findFirst();
  }

  private String where(ManagementScope scope, LocalDateTime from, LocalDateTime to,
      String action, List<Object> args) {
    StringBuilder sql = new StringBuilder(scope.where(args));
    if (from != null) {
      sql.append(" AND l.operated_at >= ?");
      args.add(from);
    }
    if (to != null) {
      sql.append(" AND l.operated_at <= ?");
      args.add(to);
    }
    if (!action.isEmpty()) {
      sql.append(" AND l.action = ?");
      args.add(action);
    }
    return sql.toString();
  }

  private OperationLogEntry map(ResultSet rs, int row) throws SQLException {
    return new OperationLogEntry(rs.getLong("id"),
        rs.getObject("operated_at", LocalDateTime.class), rs.getString("actor_type"),
        rs.getString("actor_name"), rs.getString("actor_account"),
        rs.getObject("target_activity_id", Long.class), rs.getString("activity_name"),
        rs.getObject("session_id", Long.class), rs.getObject("session_start", LocalDateTime.class),
        rs.getObject("registration_id", Long.class), rs.getString("action"), rs.getString("result"),
        rs.getString("failure_reason"), rs.getString("before_value"), rs.getString("after_value"));
  }
}
