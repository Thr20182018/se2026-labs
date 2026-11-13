package edu.nju.workshop.identity;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/** 登录会话中的身份。页面只使用姓名和角色，不读取密码哈希。 */
public class AccountUser implements UserDetails, Serializable {

  private final UserAccount account;

  public AccountUser(UserAccount account) {
    this.account = account;
  }

  public static AccountUser current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AccountUser user)) {
      throw new IllegalStateException("当前没有登录用户");
    }
    return user;
  }

  public long getId() {
    return account.id();
  }

  public String getDisplayName() {
    return account.displayName();
  }

  public Role getRole() {
    return account.role();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
  }

  @Override
  public String getPassword() {
    return account.passwordHash();
  }

  @Override
  public String getUsername() {
    return account.account();
  }
}
