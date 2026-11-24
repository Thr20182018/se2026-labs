package edu.nju.workshop.statistics;

import edu.nju.workshop.shared.ManagementScope;
import edu.nju.workshop.shared.ManagementScopeService.Choice;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 读取活动和场次的组织者归属及管理筛选选项. */
@Repository
public class ManagementScopeRepository {
  private final JdbcTemplate jdbc;

  /** 使用数据库模板查询管理范围和筛选对象. */
  public ManagementScopeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 读取活动所属的组织者，活动不存在时返回空值. */
  public Target activity(long id) {
    return jdbc.query("SELECT id, organizer_id FROM activities WHERE id = ?", (rs, row) ->
        new Target(rs.getLong("id"), rs.getLong("organizer_id")), id)
        .stream().findFirst().orElse(null);
  }

  /** 读取场次所属的活动和组织者，场次不存在时返回空值. */
  public Target session(long id) {
    return jdbc.query("""
        SELECT a.id, a.organizer_id FROM sessions s JOIN activities a ON a.id = s.activity_id
        WHERE s.id = ?
        """, (rs, row) -> new Target(rs.getLong("id"), rs.getLong("organizer_id")), id)
        .stream().findFirst().orElse(null);
  }

  /** 查询授权范围内按编号倒序排列的活动选项. */
  public List<Choice> activities(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.query("SELECT a.id, a.name FROM activities a" + scope.where(args)
        + " ORDER BY a.id DESC", (rs, row) -> new Choice(rs.getLong("id"),
            rs.getString("name") + " · #" + rs.getLong("id")), args.toArray());
  }

  /** 查询授权范围内按开始时间倒序排列的场次选项. */
  public List<Choice> sessions(ManagementScope scope) {
    List<Object> args = new ArrayList<>();
    return jdbc.query("""
        SELECT s.id, CONCAT(a.name, ' · ', DATE_FORMAT(s.start_time, '%Y-%m-%d %H:%i'),
          ' → ', DATE_FORMAT(s.end_time, '%Y-%m-%d %H:%i'), ' · #', s.id) AS label
        FROM sessions s JOIN activities a ON a.id = s.activity_id
        """ + scope.where(args) + " ORDER BY s.start_time DESC, s.id DESC",
        (rs, row) -> new Choice(rs.getLong("id"), rs.getString("label")), args.toArray());
  }

  /** 权限校验所需的活动编号和所属组织者编号. */
  public record Target(long activityId, long ownerId) {
  }
}
