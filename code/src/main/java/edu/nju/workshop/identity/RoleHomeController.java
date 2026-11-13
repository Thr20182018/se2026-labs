package edu.nju.workshop.identity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 后续任务的入口。本轮只按角色开放页面，不处理业务写入。 */
@Controller
public class RoleHomeController {

  @GetMapping("/me/registrations")
  public String registrations(Model model) {
    return page(model, "我的报名", "mine");
  }

  private String page(Model model, String title, String nav) {
    model.addAttribute("title", title);
    model.addAttribute("nav", nav);
    return "placeholder";
  }
}
