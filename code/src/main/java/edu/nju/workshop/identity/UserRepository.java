package edu.nju.workshop.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 按账号读取用户认证和角色信息. */
@Repository
public class UserRepository {

  private final JdbcTemplate jdbc;

  /** 使用数据库模板读取用户账号. */
  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 按唯一账号查询用户，不存在时返回空结果. */
  public Optional<UserAccount> findByAccount(String account) {
    List<UserAccount> rows = jdbc.query(
        "SELECT id, account, password_hash, display_name, role FROM users WHERE account = ?",
        (rs, row) -> new UserAccount(
            rs.getLong("id"),
            rs.getString("account"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role"))),
        account);
    return rows.stream().findFirst();
  }
}
