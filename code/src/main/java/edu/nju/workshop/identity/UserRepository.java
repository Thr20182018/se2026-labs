package edu.nju.workshop.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

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
