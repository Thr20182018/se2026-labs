package edu.nju.workshop.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** 展示安全过滤器转发的拒绝访问页面. */
@Controller
public class AccessDeniedController {

  /** 保留原请求方法，并以禁止访问状态展示提示页面. */
  @RequestMapping("/access-denied")
  public String denied(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return "denied";
  }
}
