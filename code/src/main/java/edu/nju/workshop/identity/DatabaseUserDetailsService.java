package edu.nju.workshop.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从数据库加载账号，为 Spring Security 提供认证身份. */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  /** 使用账号仓库读取登录所需的身份和密码哈希. */
  public DatabaseUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    UserAccount account = users.findByAccount(username)
        .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    return new AccountUser(account);
  }
}
