package edu.nju.workshop.activity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityManagementRepository {

  private final JdbcTemplate jdbc;

  public ActivityManagementRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  String role(long actorId) {
    return jdbc.query("SELECT role FROM users WHERE id = ?",
        (rs, row) -> rs.getString("role"), actorId).stream().findFirst().orElse("");
  }

  List<ActivitySummary> owned(long actorId) {
    return summaries("a.organizer_id = ?", actorId);
  }

  List<ActivitySummary> pending() {
    return summaries("a.publication_status = ?", "PENDING");
  }

  private List<ActivitySummary> summaries(String condition, Object value) {
    return jdbc.query("""
        SELECT a.id, a.name, a.publication_status, u.display_name,
          (SELECT COUNT(*) FROM sessions s WHERE s.activity_id = a.id) AS session_count
        FROM activities a JOIN users u ON u.id = a.organizer_id
        """ + " WHERE " + condition + " ORDER BY a.id DESC",
        (rs, row) -> new ActivitySummary(rs.getLong("id"), rs.getString("name"),
            PublicationStatus.valueOf(rs.getString("publication_status")),
            rs.getInt("session_count"), rs.getString("display_name")), value);
  }

  Optional<ManagedActivity> find(long id, boolean lock) {
    String locking = lock ? " FOR UPDATE" : "";
    List<ManagedActivity> rows = jdbc.query("""
        SELECT a.*, (SELECT display_name FROM users u WHERE u.id = a.organizer_id) AS owner_name
        FROM activities a WHERE a.id = ?
        """ + locking, (rs, row) -> new ManagedActivity(
            rs.getLong("id"), rs.getString("name"), rs.getString("description"),
            rs.getLong("organizer_id"), rs.getString("owner_name"),
            PublicationStatus.valueOf(rs.getString("publication_status")),
            rs.getLong("review_version"), List.of()), id);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    ManagedActivity activity = rows.getFirst();
    List<ManagedSession> sessions = jdbc.query(
        "SELECT * FROM sessions WHERE activity_id = ? ORDER BY id" + locking,
        (rs, row) -> session(rs), id);
    return Optional.of(new ManagedActivity(activity.id(), activity.name(), activity.description(),
        activity.organizerId(), activity.organizerName(), activity.status(),
        activity.reviewVersion(), sessions));
  }

  long create(long actorId, ActivityDraft draft) {
    return insert("""
        INSERT INTO activities (name, description, organizer_id, publication_status)
        VALUES (?,?,?,'DRAFT')
        """, draft.name(), draft.description(), actorId);
  }

  void edit(long id, ActivityDraft draft) {
    jdbc.update("UPDATE activities SET name = ?, description = ? WHERE id = ?",
        draft.name(), draft.description(), id);
  }

  long addSession(long activityId, ManagedSession session) {
    return insert("""
        INSERT INTO sessions (activity_id, start_time, end_time, registration_deadline,
          location, capacity, publication_status)
        VALUES (?,?,?,?,?,?,'DRAFT')
        """, activityId, session.startTime(), session.endTime(), session.deadline(),
        session.location(), session.capacity());
  }

  void editSession(long activityId, ManagedSession session) {
    jdbc.update("""
        UPDATE sessions SET start_time = ?, end_time = ?, registration_deadline = ?,
          location = ?, capacity = ? WHERE id = ? AND activity_id = ?
        """, session.startTime(), session.endTime(), session.deadline(), session.location(),
        session.capacity(), session.id(), activityId);
  }

  void submitForReview(long id) {
    jdbc.update("UPDATE activities SET review_version = review_version + 1 WHERE id = ?", id);
    setStatus(id, PublicationStatus.PENDING);
  }

  void setStatus(long id, PublicationStatus status) {
    jdbc.update("UPDATE activities SET publication_status = ? WHERE id = ?", status.name(), id);
    jdbc.update("UPDATE sessions SET publication_status = ? WHERE activity_id = ?",
        status.name(), id);
  }

  private long insert(String sql, Object... values) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < values.length; i++) {
        statement.setObject(i + 1, values[i]);
      }
      return statement;
    }, keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  private ManagedSession session(ResultSet rs) throws SQLException {
    return new ManagedSession(rs.getLong("id"), rs.getTimestamp("start_time").toLocalDateTime(),
        rs.getTimestamp("end_time").toLocalDateTime(),
        rs.getTimestamp("registration_deadline").toLocalDateTime(), rs.getString("location"),
        rs.getInt("capacity"), PublicationStatus.valueOf(rs.getString("publication_status")));
  }
}
