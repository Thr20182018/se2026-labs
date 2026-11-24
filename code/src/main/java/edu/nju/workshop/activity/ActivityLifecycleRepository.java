package edu.nju.workshop.activity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保存活动与场次的取消状态，并清理失效的待审内容. */
@Repository
public class ActivityLifecycleRepository {
  private final JdbcTemplate jdbc;

  /** 使用业务数据库执行活动和场次的取消更新. */
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
