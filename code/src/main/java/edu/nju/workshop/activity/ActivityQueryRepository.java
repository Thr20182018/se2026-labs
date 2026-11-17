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
          + " WHEN s.publication_status = 'CANCELLED' THEN 'CANCELLED'"
          + " WHEN s.registration_deadline <= ? THEN 'CLOSED'"
          + " WHEN s.capacity <= COALESCE(r.registered_count, 0)"
          + " OR COALESCE(r.waitlisted_count, 0) > 0 THEN 'WAITLIST'"
          + " ELSE 'OPEN' END";

  private static final String COLUMNS =
      "s.id, a.name, a.description, s.start_time, s.end_time, s.registration_deadline,"
          + " s.location, s.capacity, COALESCE(r.registered_count, 0) AS registered_count";

  private static final String ORDER = "CASE WHEN s.publication_status = 'PUBLISHED'"
      + " AND s.registration_deadline > ? THEN 0 ELSE 1 END, s.start_time ASC, s.id ASC";

  private static final String FROM =
      " FROM sessions s"
          + " JOIN activities a ON a.id = s.activity_id"
          + " LEFT JOIN ("
          + "SELECT session_id, SUM(status IN ('REGISTERED', 'CHECKED_IN', 'ABSENT'))"
          + " AS registered_count, SUM(status = 'WAITLIST') AS waitlisted_count FROM registrations"
          + " GROUP BY session_id) r ON r.session_id = s.id";

  private final JdbcTemplate jdbc;

  public ActivityQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public QueryPage search(ActivityQuery query, LocalDateTime now, int pageSize, long selected) {
    List<Object> filters = new ArrayList<>();
    String where = where(query, now, filters);
    int total = jdbc.queryForObject(
        "SELECT COUNT(*)" + FROM + where, Integer.class, filters.toArray());
    int page = Math.min(query.page(), Math.max(1, (total + pageSize - 1) / pageSize));
    if (selected > 0) {
      page = selectedPage(where, filters, now, selected, pageSize, page);
    }
    List<Object> args = new ArrayList<>();
    args.add(now);
    args.addAll(filters);
    args.add(now);
    args.add(pageSize);
    args.add((page - 1L) * pageSize);
    List<SessionRow> rows = jdbc.query(
        "SELECT " + COLUMNS + ", " + STATUS + " AS availability" + FROM + where
            + " ORDER BY " + ORDER + " LIMIT ? OFFSET ?",
        ActivityQueryRepository::map,
        args.toArray());
    return new QueryPage(rows, total, page, pageSize);
  }

  private int selectedPage(String where, List<Object> filters, LocalDateTime now, long selected,
      int pageSize, int fallback) {
    List<Object> args = new ArrayList<>();
    args.add(now);
    args.addAll(filters);
    args.add(selected);
    var positions = jdbc.query("SELECT row_position FROM (SELECT s.id,"
        + " ROW_NUMBER() OVER (ORDER BY " + ORDER + ") AS row_position"
        + FROM + where + ") ranked WHERE id = ?", (rs, row) -> rs.getLong(1), args.toArray());
    return positions.isEmpty() ? fallback
        : Math.toIntExact((positions.getFirst() - 1) / pageSize + 1);
  }

  private static String where(ActivityQuery query, LocalDateTime now, List<Object> args) {
    StringBuilder sql = new StringBuilder();
    sql.append(" WHERE a.publication_status IN ('PUBLISHED', 'CANCELLED')");
    sql.append(" AND (s.publication_status = 'PUBLISHED'"
        + " OR (s.publication_status = 'CANCELLED' AND s.published_before_cancel = TRUE))");
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
        rs.getObject("start_time", LocalDateTime.class),
        rs.getObject("end_time", LocalDateTime.class),
        rs.getObject("registration_deadline", LocalDateTime.class),
        rs.getString("location"),
        rs.getInt("capacity"),
        rs.getInt("registered_count"),
        Availability.valueOf(rs.getString("availability")));
  }
}
