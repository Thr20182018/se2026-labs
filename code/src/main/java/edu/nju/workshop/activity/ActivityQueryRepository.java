package edu.nju.workshop.activity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityQueryRepository {

  static final String STATUS =
      "CASE"
          + " WHEN a.publication_status = 'CANCELLED'"
          + " OR s.publication_status = 'CANCELLED' THEN 'CANCELLED'"
          + " WHEN s.registration_deadline <= ? THEN 'CLOSED'"
          + " WHEN s.capacity <= COALESCE(r.registered_count, 0) THEN 'WAITLIST'"
          + " ELSE 'OPEN' END";

  private static final String COLUMNS =
      "s.id, a.name, a.description, s.start_time, s.end_time, s.registration_deadline,"
          + " s.location, s.capacity, COALESCE(r.registered_count, 0) AS registered_count";

  private static final String FROM =
      " FROM sessions s"
          + " JOIN activities a ON a.id = s.activity_id"
          + " LEFT JOIN ("
          + "SELECT session_id, COUNT(*) AS registered_count FROM registrations"
          + " WHERE status IN ('REGISTERED', 'CHECKED_IN', 'ABSENT')"
          + " GROUP BY session_id) r ON r.session_id = s.id";

  private final JdbcTemplate jdbc;

  public ActivityQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public QueryPage search(ActivityQuery query, LocalDateTime now, int pageSize) {
    List<Object> filters = new ArrayList<>();
    String where = where(query, now, filters);
    Integer total = jdbc.queryForObject(
        "SELECT COUNT(*)" + FROM + where, Integer.class, filters.toArray());
    List<Object> args = new ArrayList<>();
    args.add(now);
    args.addAll(filters);
    args.add(pageSize);
    args.add((query.page() - 1L) * pageSize);
    List<SessionRow> rows = jdbc.query(
        "SELECT " + COLUMNS + ", " + STATUS + " AS availability" + FROM + where
            + " ORDER BY s.start_time ASC, s.id ASC LIMIT ? OFFSET ?",
        ActivityQueryRepository::map,
        args.toArray());
    return new QueryPage(rows, total == null ? 0 : total, query.page(), pageSize);
  }

  private static String where(ActivityQuery query, LocalDateTime now, List<Object> args) {
    StringBuilder sql = new StringBuilder();
    sql.append(" WHERE a.publication_status IN ('PUBLISHED', 'CANCELLED')");
    sql.append(" AND s.publication_status IN ('PUBLISHED', 'CANCELLED')");
    if (query.keyword() != null) {
      sql.append(" AND a.name LIKE ? ESCAPE '\\\\'");
      args.add(like(query.keyword()));
    }
    if (query.dateFrom() != null) {
      sql.append(" AND DATE(s.start_time) >= ?");
      args.add(query.dateFrom());
    }
    if (query.dateTo() != null) {
      sql.append(" AND DATE(s.start_time) <= ?");
      args.add(query.dateTo());
    }
    if (query.availability() != null) {
      sql.append(" AND (").append(STATUS).append(") = ?");
      args.add(now);
      args.add(query.availability().name());
    }
    return sql.toString();
  }

  static String like(String keyword) {
    String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private static SessionRow map(ResultSet rs, int row) throws SQLException {
    return new SessionRow(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getTimestamp("start_time").toLocalDateTime(),
        rs.getTimestamp("end_time").toLocalDateTime(),
        rs.getTimestamp("registration_deadline").toLocalDateTime(),
        rs.getString("location"),
        rs.getInt("capacity"),
        rs.getInt("registered_count"),
        Availability.valueOf(rs.getString("availability")));
  }
}
