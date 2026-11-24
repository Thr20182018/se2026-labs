package edu.nju.workshop.audit;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.shared.ManagementScope;
import edu.nju.workshop.shared.ManagementScopeService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OperationLogQueryController {

  private final ManagementScopeService scopes;
  private final OperationLogQueryService service;

  public OperationLogQueryController(ManagementScopeService scopes,
      OperationLogQueryService service) {
    this.scopes = scopes;
    this.service = service;
  }

  @GetMapping({"/organizer/logs", "/admin/logs"})
  public String logs(@RequestParam(required = false) Long activityId,
      @RequestParam(required = false) Long sessionId,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @RequestParam(defaultValue = "") String action,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(required = false) Long selected, Model model) {
    AccountUser user = AccountUser.current();
    ManagementScope scope = scopes.resolve(user, activityId, sessionId);
    model.addAttribute("pageData", service.search(scope, from, to, action, page, selected));
    model.addAttribute("scope", scope);
    model.addAttribute("activities", scopes.activities(scope));
    model.addAttribute("sessions", scopes.sessions(scope));
    model.addAttribute("basePath", scopes.basePath(user));
    model.addAttribute("actions", OperationLogAction.values());
    model.addAttribute("from", from);
    model.addAttribute("to", to);
    model.addAttribute("action", action.trim());
    return "operation-logs";
  }

  @ExceptionHandler(ActivityException.class)
  public String failure(ActivityException ex, HttpServletResponse response, Model model) {
    response.setStatus(ex.status());
    model.addAttribute("message", ex.getMessage());
    return ex.status() == 403 ? "denied" : "management-query-error";
  }
}
