package edu.nju.workshop.identity;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 只读取组织者本人活动的已发布名称，供越权校验。 */
@Repository
public class ActivityGate {

  private final JdbcTemplate jdbc;

  public ActivityGate(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> findNameForOrganizer(long activityId, long organizerId) {
    return jdbc.query(
        "SELECT name FROM activities WHERE id = ? AND organizer_id = ?",
        (rs, row) -> rs.getString("name"),
        activityId,
        organizerId).stream().findFirst();
  }
}
