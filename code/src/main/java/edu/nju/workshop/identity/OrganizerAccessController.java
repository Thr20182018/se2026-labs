package edu.nju.workshop.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OrganizerAccessController {

  private final ActivityGate activities;

  public OrganizerAccessController(ActivityGate activities) {
    this.activities = activities;
  }

  @GetMapping("/organizer/activities/{id}")
  public String activity(@PathVariable long id, HttpServletResponse response, Model model) {
    AccountUser user = AccountUser.current();
    String name = activities.findNameForOrganizer(id, user.getId()).orElse(null);
    if (name == null) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "denied";
    }
    model.addAttribute("activityName", name);
    return "organizer-activity";
  }
}
