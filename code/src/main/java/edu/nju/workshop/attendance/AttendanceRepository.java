package edu.nju.workshop.attendance;

import edu.nju.workshop.activity.PublicationStatus;
import edu.nju.workshop.registration.RegistrationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceRepository {
  private final JdbcTemplate jdbc;

  public AttendanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Session lock(long sessionId) {
    return jdbc.query("SELECT * FROM sessions WHERE id = ? FOR UPDATE", (rs, row) ->
        new Session(rs.getLong("id"), rs.getLong("activity_id"),
            rs.getObject("end_time", LocalDateTime.class),
            PublicationStatus.valueOf(rs.getString("publication_status"))), sessionId)
        .stream().findFirst().orElse(null);
  }

  List<AttendanceRow> attendees(long sessionId) {
    return jdbc.query("""
        SELECT r.id, u.display_name, u.account, r.status, r.checked_in_at
        FROM registrations r JOIN users u ON u.id = r.participant_id
        WHERE r.session_id = ? ORDER BY r.id
        """, (rs, row) -> new AttendanceRow(rs.getLong("id"), rs.getString("display_name"),
            rs.getString("account"), RegistrationStatus.valueOf(rs.getString("status")),
            rs.getObject("checked_in_at", LocalDateTime.class)), sessionId);
  }

  Entry registration(long sessionId, long registrationId) {
    return jdbc.query("""
        SELECT id, status, checked_in_at FROM registrations WHERE session_id = ? AND id = ?
        """, (rs, row) -> entry(rs), sessionId, registrationId).stream().findFirst().orElse(null);
  }

  List<Entry> registered(long sessionId) {
    return jdbc.query("""
        SELECT id, status, checked_in_at FROM registrations
        WHERE session_id = ? AND status = 'REGISTERED' ORDER BY id
        """, (rs, row) -> entry(rs), sessionId);
  }

  void checkIn(long registrationId, LocalDateTime time) {
    jdbc.update("UPDATE registrations SET status = 'CHECKED_IN', checked_in_at = ? WHERE id = ?",
        time, registrationId);
  }

  List<Long> endedSessions(LocalDateTime now) {
    return jdbc.query("""
        SELECT s.id FROM sessions s WHERE s.end_time <= ? AND s.publication_status = 'PUBLISHED'
          AND EXISTS(SELECT 1 FROM registrations r
            WHERE r.session_id = s.id AND r.status = 'REGISTERED')
        ORDER BY s.id
        """, (rs, row) -> rs.getLong("id"), now);
  }

  void markAbsent(long registrationId) {
    jdbc.update("UPDATE registrations SET status = 'ABSENT' WHERE id = ?", registrationId);
  }

  private Entry entry(ResultSet rs) throws SQLException {
    return new Entry(rs.getLong("id"), RegistrationStatus.valueOf(rs.getString("status")),
        rs.getObject("checked_in_at", LocalDateTime.class));
  }

  record Session(long id, long activityId, LocalDateTime end, PublicationStatus status) {
  }

  record Entry(long id, RegistrationStatus status, LocalDateTime checkedInAt) {
  }
}
