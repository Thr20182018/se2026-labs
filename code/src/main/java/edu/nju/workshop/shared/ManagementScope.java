package edu.nju.workshop.shared;

import java.util.List;

/** 管理查询的已授权范围，查询统一使用活动别名 a、场次别名 s. */
public record ManagementScope(Long ownerId, Long activityId, Long sessionId) {

  /** 生成权限范围的 SQL 条件，并按占位符顺序追加查询参数. */
  public String where(List<Object> args) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    if (ownerId != null) {
      sql.append(" AND a.organizer_id = ?");
      args.add(ownerId);
    }
    if (activityId != null) {
      sql.append(" AND a.id = ?");
      args.add(activityId);
    }
    if (sessionId != null) {
      sql.append(" AND s.id = ?");
      args.add(sessionId);
    }
    return sql.toString();
  }
}
