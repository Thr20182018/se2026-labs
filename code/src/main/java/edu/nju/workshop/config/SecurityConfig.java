package edu.nju.workshop.config;

import edu.nju.workshop.identity.AuditedAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** 配置登录会话、角色路径权限和密码校验方式. */
@Configuration
public class SecurityConfig {

  /** 建立三类角色的请求权限、表单登录及拒绝访问审计规则. */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      AuditedAccessDeniedHandler deniedHandler) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/error").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .requestMatchers("/organizer/**").hasRole("ORGANIZER")
            .requestMatchers("/me/**").hasRole("PARTICIPANT")
            .anyRequest().authenticated())
        .formLogin(form -> form
            .loginPage("/login")
            .loginProcessingUrl("/login")
            .defaultSuccessUrl("/workshops", true)
            .failureUrl("/login?error")
            .permitAll())
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout")
            .invalidateHttpSession(true))
        .exceptionHandling(denied -> denied.accessDeniedHandler(deniedHandler));
    return http.build();
  }

  /** 提供用于账号密码校验的 BCrypt 编码器. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
