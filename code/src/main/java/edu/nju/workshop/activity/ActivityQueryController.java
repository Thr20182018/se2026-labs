package edu.nju.workshop.activity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ActivityQueryController {

  static final int PAGE_SIZE = 8;

  private final ActivityQueryService service;

  public ActivityQueryController(ActivityQueryService service) {
    this.service = service;
  }

  @GetMapping("/workshops")
  public String workshops(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String availability,
      @RequestParam(name = "page", required = false) String page,
      Model model) {
    model.addAttribute("keyword", keyword == null ? "" : keyword);
    model.addAttribute("dateFrom", blank(dateFrom));
    model.addAttribute("dateTo", blank(dateTo));
    model.addAttribute("availability", availability == null ? "" : availability);
    try {
      ActivityQuery query = ActivityQuery.parse(keyword, dateFrom, dateTo, availability, page);
      model.addAttribute("result", service.search(query, PAGE_SIZE));
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
