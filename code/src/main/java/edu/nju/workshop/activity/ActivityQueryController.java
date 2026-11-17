package edu.nju.workshop.activity;

import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.identity.Role;
import edu.nju.workshop.registration.RegistrationService;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ActivityQueryController {

  static final int PAGE_SIZE = 8;

  private final ActivityQueryService service;
  private final RegistrationService registrations;

  public ActivityQueryController(ActivityQueryService service, RegistrationService registrations) {
    this.service = service;
    this.registrations = registrations;
  }

  @GetMapping("/workshops")
  public String workshops(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String availability,
      @RequestParam(name = "page", required = false) String page,
      @RequestParam(defaultValue = "0") long selected,
      Model model) {
    model.addAttribute("keyword", keyword == null ? "" : keyword);
    model.addAttribute("dateFrom", blank(dateFrom));
    model.addAttribute("dateTo", blank(dateTo));
    model.addAttribute("availability", availability == null ? "" : availability);
    boolean participant = AccountUser.current().getRole() == Role.PARTICIPANT;
    model.addAttribute("participant", participant);
    try {
      ActivityQuery query = ActivityQuery.parse(keyword, dateFrom, dateTo, availability, page);
      QueryPage result = service.search(query, PAGE_SIZE);
      model.addAttribute("result", result);
      model.addAttribute("selectedId", selected);
      model.addAttribute("registrationOffers", participant
          ? registrations.offers(AccountUser.current().getId(), result.rows()) : Map.of());
    } catch (IllegalArgumentException ex) {
      model.addAttribute("message", ex.getMessage());
    }
    return "workshops";
  }

  private static String blank(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return raw.trim().replace('/', '-');
  }
}
