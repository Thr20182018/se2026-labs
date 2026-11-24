package edu.nju.workshop.statistics;

import edu.nju.workshop.shared.ManagementScope;
import edu.nju.workshop.shared.ManagementScopeService.Choice;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManagementScopeRepository {
  private final JdbcTemplate jdbc;

  public ManagementScopeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Target activity(long id) {
    return jdbc.query("SELECT id, organizer_id FROM activities WHERE id = ?", (rs, row) ->
        new Target(rs.getLong("id"), rs.getLong("organizer_id")), id)
        .stream().findFirst().orElse(null);
  }

  public Target session(long id) {
    return jdbc.query("""
        SELECT a.id, a.organizer_id FROM sessions s JOIN activities a ON a.id = s.activity_id
        WHERE s.id = ?
        """, (rs, row) -> new Target(rs.getLong("id"), rs.getLong("organizer_id")), id)
        .stream().findFirst().orElse(null);
  }

  public List<Choice> activities(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.query("SELECT a.id, a.name FROM activities a" + scope.where(args)
        + " ORDER BY a.id DESC", (rs, row) -> new Choice(rs.getLong("id"),
            rs.getString("name") + " · #" + rs.getLong("id")), args.toArray());
  }

  public List<Choice> sessions(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.query("""
        SELECT s.id, CONCAT(a.name, ' · ', DATE_FORMAT(s.start_time, '%Y-%m-%d %H:%i'),
          ' → ', DATE_FORMAT(s.end_time, '%Y-%m-%d %H:%i'), ' · #', s.id) AS label
        FROM sessions s JOIN activities a ON a.id = s.activity_id
        """ + scope.where(args) + " ORDER BY s.start_time DESC, s.id DESC",
        (rs, row) -> new Choice(rs.getLong("id"), rs.getString("label")), args.toArray());
  }

  public record Target(long activityId, long ownerId) {
  }
}
