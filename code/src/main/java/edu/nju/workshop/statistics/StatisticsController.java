package edu.nju.workshop.statistics;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.shared.ManagementScopeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StatisticsController {
  private final StatisticsService service;
  private final ManagementScopeService scopes;

  public StatisticsController(StatisticsService service, ManagementScopeService scopes) {
    this.service = service;
    this.scopes = scopes;
  }

  @GetMapping({"/organizer/statistics", "/admin/statistics"})
  public String statistics(@RequestParam(required = false) Long activityId,
      @RequestParam(required = false) Long sessionId, @RequestParam(defaultValue = "1") int page,
      Model model) {
    AccountUser user = AccountUser.current();
    var result = service.search(user, activityId, sessionId, page);
    model.addAttribute("scope", result.scope());
    model.addAttribute("activities", result.activities());
    model.addAttribute("sessions", result.sessions());
    model.addAttribute("pageData", result.pageData());
    model.addAttribute("attendanceActivityId", result.attendanceActivityId());
    model.addAttribute("basePath", scopes.basePath(user));
    return "statistics";
  }

  @GetMapping({"/organizer/statistics/registrations.csv", "/admin/statistics/registrations.csv"})
  public ResponseEntity<byte[]> registrations(@RequestParam(required = false) Long activityId,
      @RequestParam(required = false) Long sessionId) {
    return download("registrations.csv",
        service.registrationsCsv(AccountUser.current(), activityId, sessionId));
  }

  @GetMapping({"/organizer/statistics/summary.csv", "/admin/statistics/summary.csv"})
  public ResponseEntity<byte[]> summary(@RequestParam(required = false) Long activityId,
      @RequestParam(required = false) Long sessionId) {
    return download("summary.csv",
        service.summaryCsv(AccountUser.current(), activityId, sessionId));
  }

  private ResponseEntity<byte[]> download(String filename, byte[] bytes) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(bytes);
  }

  @ExceptionHandler(ActivityException.class)
  public String readFailure(ActivityException ex, HttpServletResponse response, Model model) {
    response.setStatus(ex.status());
    model.addAttribute("message", ex.getMessage());
    return ex.status() == 403 ? "denied" : "management-query-error";
  }
}
