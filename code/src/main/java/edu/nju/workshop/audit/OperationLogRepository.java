package edu.nju.workshop.audit;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationLogRepository {

  private final JdbcTemplate jdbc;

  public OperationLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void insert(LocalDateTime time, Long actorId, Long activityId, Long sessionId,
      String action, String result, String reason, String before, String after) {
    jdbc.update("""
        INSERT INTO operation_logs
          (operated_at, actor_type, actor_id, activity_id, session_id, action,
           result, failure_reason, before_value, after_value)
        VALUES (?,?,?,?,?,?,?,?,?,?)
        """, time, actorId == null ? "SYSTEM" : "USER", actorId, activityId, sessionId,
        action, result, reason, before, after);
  }
}
