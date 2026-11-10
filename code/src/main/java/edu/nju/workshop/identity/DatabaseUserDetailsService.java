package edu.nju.workshop.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

  private final UserRepository users;

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
