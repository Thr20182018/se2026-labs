package edu.nju.workshop.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessDeniedController {

  @GetMapping("/access-denied")
  public String denied(HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return "denied";
  }
}
