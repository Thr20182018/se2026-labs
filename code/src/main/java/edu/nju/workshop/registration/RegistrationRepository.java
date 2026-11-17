package edu.nju.workshop.registration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationRepository {
  private final JdbcTemplate jdbc;

  public RegistrationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  boolean participant(long actorId) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND role = 'PARTICIPANT'",
        Integer.class, actorId) == 1;
  }

  Session lock(long sessionId) {
    return jdbc.query("SELECT * FROM sessions WHERE id = ? FOR UPDATE", (rs, row) ->
        new Session(rs.getLong("id"), rs.getLong("activity_id"), rs.getInt("capacity"),
            rs.getObject("registration_deadline", LocalDateTime.class),
            rs.getObject("end_time", LocalDateTime.class), rs.getString("publication_status")),
        sessionId).stream().findFirst().orElse(null);
  }

  boolean activityPublished(long activityId) {
    return jdbc.queryForObject("SELECT publication_status FROM activities WHERE id = ?",
        String.class, activityId).equals("PUBLISHED");
  }

  int registered(long sessionId) {
    return jdbc.queryForObject("""
        SELECT COUNT(*) FROM registrations WHERE session_id = ?
          AND status IN ('REGISTERED', 'CHECKED_IN', 'ABSENT')
        """, Integer.class, sessionId);
  }

  boolean hasWaitlist(long sessionId) {
    return jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM registrations WHERE session_id = ? AND status = 'WAITLIST')
        """, Boolean.class, sessionId);
  }

  Registration active(long actorId, long sessionId) {
    return jdbc.query("""
        SELECT id, status, waitlist_order FROM registrations
        WHERE participant_id = ? AND session_id = ? AND status <> 'CANCELLED'
        """, (rs, row) -> registration(rs), actorId, sessionId).stream().findFirst().orElse(null);
  }

  Registration owned(long actorId, long sessionId, long registrationId) {
    return jdbc.query("""
        SELECT id, status, waitlist_order FROM registrations
        WHERE participant_id = ? AND session_id = ? AND id = ?
        """, (rs, row) -> registration(rs), actorId, sessionId, registrationId)
        .stream().findFirst().orElse(null);
  }

  boolean organizerOwns(long actorId, long activityId) {
    return jdbc.queryForObject("""
        SELECT COUNT(*) FROM activities a JOIN users u ON u.id = a.organizer_id
        WHERE a.id = ? AND u.id = ? AND u.role = 'ORGANIZER'
        """, Integer.class, activityId, actorId) == 1;
  }

  void resize(long sessionId, int capacity) {
    jdbc.update("UPDATE sessions SET capacity = ? WHERE id = ?", capacity, sessionId);
  }

  void setStatus(long registrationId, RegistrationStatus status) {
    jdbc.update("UPDATE registrations SET status = ? WHERE id = ?", status.name(), registrationId);
  }

  List<Registration> cancellable(long sessionId) {
    return jdbc.query("""
        SELECT id, status, waitlist_order FROM registrations WHERE session_id = ?
          AND status IN ('REGISTERED', 'WAITLIST', 'CHECKED_IN') ORDER BY id
        """, (rs, row) -> registration(rs), sessionId);
  }

  Map<Long, Registration> active(long actorId, List<Long> sessionIds) {
    Map<Long, Registration> result = new HashMap<>();
    if (sessionIds.isEmpty()) {
      return result;
    }
    List<Object> args = new ArrayList<>();
    args.add(actorId);
    args.addAll(sessionIds);
    String placeholders = String.join(",", sessionIds.stream().map(id -> "?").toList());
    jdbc.query("SELECT session_id, id, status, waitlist_order FROM registrations"
        + " WHERE participant_id = ? AND status <> 'CANCELLED' AND session_id IN ("
        + placeholders + ")", rs -> {
          result.put(rs.getLong("session_id"), registration(rs));
        }, args.toArray());
    return result;
  }

  Registration insert(long actorId, Session session, boolean waitlist, LocalDateTime receivedAt) {
    Integer order = waitlist ? jdbc.queryForObject("""
        SELECT COALESCE(MAX(waitlist_order), 0) + 1 FROM registrations WHERE session_id = ?
        """, Integer.class, session.id()) : null;
    RegistrationStatus status = waitlist
        ? RegistrationStatus.WAITLIST : RegistrationStatus.REGISTERED;
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var statement = connection.prepareStatement("""
          INSERT INTO registrations
            (participant_id, session_id, registered_at, status, waitlist_order)
          VALUES (?,?,?,?,?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setLong(1, actorId);
      statement.setLong(2, session.id());
      statement.setObject(3, receivedAt.withNano(0));
      statement.setString(4, status.name());
      statement.setObject(5, order);
      return statement;
    }, keys);
    return new Registration(Objects.requireNonNull(keys.getKey()).longValue(), status, order);
  }

  List<RegistrationRow> mine(long actorId) {
    return jdbc.query("""
        SELECT r.*, a.name, s.location, s.start_time, s.end_time, s.registration_deadline,
          s.publication_status AS session_status, a.publication_status AS activity_status
        FROM registrations r JOIN sessions s ON s.id = r.session_id
          JOIN activities a ON a.id = s.activity_id
        WHERE r.participant_id = ? ORDER BY r.registered_at DESC, r.id DESC
        """, (rs, row) -> new RegistrationRow(rs.getLong("id"), rs.getLong("session_id"),
            rs.getString("name"), rs.getString("location"),
            rs.getObject("start_time", LocalDateTime.class),
            rs.getObject("end_time", LocalDateTime.class),
            rs.getObject("registered_at", LocalDateTime.class),
            RegistrationStatus.valueOf(rs.getString("status")),
            rs.getObject("waitlist_order", Integer.class),
            rs.getObject("registration_deadline", LocalDateTime.class),
            "PUBLISHED".equals(rs.getString("session_status"))
                && "PUBLISHED".equals(rs.getString("activity_status"))), actorId);
  }

  List<Long> dueSessions(LocalDateTime now) {
    return jdbc.query("""
        SELECT s.id FROM sessions s WHERE s.registration_deadline <= ?
          AND s.publication_status = 'PUBLISHED'
          AND EXISTS(SELECT 1 FROM registrations r
            WHERE r.session_id = s.id AND r.status = 'WAITLIST')
        ORDER BY s.id
        """, (rs, row) -> rs.getLong("id"), now);
  }

  List<Registration> waitlist(long sessionId) {
    return jdbc.query("""
        SELECT id, status, waitlist_order FROM registrations
        WHERE session_id = ? AND status = 'WAITLIST' ORDER BY waitlist_order, id
        """, (rs, row) -> registration(rs), sessionId);
  }

  void expire(long id) {
    jdbc.update("UPDATE registrations SET status = 'WAITLIST_EXPIRED' WHERE id = ?", id);
  }

  private Registration registration(ResultSet rs) throws SQLException {
    return new Registration(rs.getLong("id"), RegistrationStatus.valueOf(rs.getString("status")),
        rs.getObject("waitlist_order", Integer.class));
  }

  record Session(long id, long activityId, int capacity, LocalDateTime deadline,
      LocalDateTime end, String status) {
  }
}
