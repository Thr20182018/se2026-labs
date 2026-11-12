package edu.nju.workshop.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AccessDeniedController {

  // 安全过滤器转发时保留原请求方法。
  @RequestMapping("/access-denied")
  public String denied(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return "denied";
  }
}
