package edu.nju.workshop.identity;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 提供首页跳转及登录页面入口. */
@Controller
public class LoginController {

  /** 将应用首页跳转到工作坊查询页面. */
  @GetMapping("/")
  public String home() {
    return "redirect:/workshops";
  }

  /** 为未登录用户展示登录页，已登录用户进入工作坊查询. */
  @GetMapping("/login")
  public String login() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      return "redirect:/workshops";
    }
    return "login";
  }
}
