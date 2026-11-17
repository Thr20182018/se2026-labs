package edu.nju.workshop.activity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
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

  List<ReviewRequest> pendingReviews() {
    return jdbc.query("""
        SELECT a.id AS activity_id, NULL AS session_id, a.name, u.display_name,
          'FIRST' AS kind
        FROM activities a JOIN users u ON u.id = a.organizer_id
        WHERE a.publication_status = 'PENDING'
        UNION ALL
        SELECT a.id, NULL, a.name, u.display_name, 'REVISION'
        FROM activities a JOIN users u ON u.id = a.organizer_id
        WHERE a.publication_status = 'PUBLISHED' AND a.revision_pending = 1
        UNION ALL
        SELECT a.id, s.id, CONCAT(a.name, ' · ', DATE_FORMAT(s.start_time, '%Y-%m-%d %H:%i')),
          u.display_name, 'SESSION'
        FROM activities a JOIN users u ON u.id = a.organizer_id
          JOIN sessions s ON s.activity_id = a.id
        WHERE a.publication_status = 'PUBLISHED' AND s.publication_status = 'PENDING'
        ORDER BY activity_id DESC, kind, session_id
        """, (rs, row) -> new ReviewRequest(rs.getLong("activity_id"),
            rs.getObject("session_id", Long.class), rs.getString("name"),
            rs.getString("display_name"), ReviewKind.valueOf(rs.getString("kind"))));
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
            rs.getLong("review_version"), rs.getString("pending_name"),
            rs.getString("pending_description"), rs.getBoolean("revision_pending"), List.of()), id);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    ManagedActivity activity = rows.getFirst();
    List<ManagedSession> sessions = jdbc.query(
        "SELECT * FROM sessions WHERE activity_id = ? ORDER BY id" + locking,
        (rs, row) -> session(rs), id);
    return Optional.of(new ManagedActivity(activity.id(), activity.name(), activity.description(),
        activity.organizerId(), activity.organizerName(), activity.status(),
        activity.reviewVersion(), activity.pendingName(), activity.pendingDescription(),
        activity.revisionPending(), sessions));
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

  void submitRevision(long id, ActivityDraft draft) {
    jdbc.update("""
        UPDATE activities SET pending_name = ?, pending_description = ?, revision_pending = 1,
          review_version = review_version + 1 WHERE id = ?
        """, draft.name(), draft.description(), id);
  }

  void decideRevision(long id, boolean approve) {
    if (approve) {
      jdbc.update("""
          UPDATE activities SET name = pending_name, description = pending_description WHERE id = ?
          """, id);
    }
    jdbc.update("""
        UPDATE activities SET pending_name = NULL, pending_description = NULL, revision_pending = 0
        WHERE id = ?
        """, id);
  }

  void submitSession(long id, long sessionId) {
    jdbc.update("""
        UPDATE sessions SET publication_status = 'PENDING', review_version = review_version + 1
        WHERE id = ? AND activity_id = ?
        """, sessionId, id);
  }

  void setSessionStatus(long id, long sessionId, PublicationStatus status) {
    jdbc.update("UPDATE sessions SET publication_status = ? WHERE id = ? AND activity_id = ?",
        status.name(), sessionId, id);
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
    return new ManagedSession(rs.getLong("id"), rs.getObject("start_time", LocalDateTime.class),
        rs.getObject("end_time", LocalDateTime.class),
        rs.getObject("registration_deadline", LocalDateTime.class), rs.getString("location"),
        rs.getInt("capacity"), PublicationStatus.valueOf(rs.getString("publication_status")),
        rs.getLong("review_version"));
  }
}
