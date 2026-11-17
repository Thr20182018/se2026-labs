package edu.nju.workshop.activity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityLifecycleRepository {
  private final JdbcTemplate jdbc;

  public ActivityLifecycleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void cancelActivity(long activityId) {
    jdbc.update("""
        UPDATE activities SET publication_status = 'CANCELLED', pending_name = NULL,
          pending_description = NULL, revision_pending = 0 WHERE id = ?
        """, activityId);
  }

  void cancelSession(long activityId, long sessionId) {
    jdbc.update("""
        UPDATE sessions SET published_before_cancel = (publication_status = 'PUBLISHED'),
          publication_status = 'CANCELLED' WHERE id = ? AND activity_id = ?
        """, sessionId, activityId);
  }
}
