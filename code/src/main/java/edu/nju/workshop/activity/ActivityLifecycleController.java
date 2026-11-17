package edu.nju.workshop.activity;

import edu.nju.workshop.identity.AccountUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ActivityLifecycleController {
  private final ActivityLifecycleService service;

  public ActivityLifecycleController(ActivityLifecycleService service) {
    this.service = service;
  }

  @GetMapping("/admin/activities")
  public String activities(Model model) {
    model.addAttribute("activities", service.all(actor()));
    return "admin-activities";
  }

  @GetMapping("/admin/activities/{id}")
  public String activity(@PathVariable long id, Model model) {
    model.addAttribute("activity", service.forAdmin(actor(), id));
    return "admin-activity";
  }

  @PostMapping({"/organizer/activities/{id}/cancel", "/admin/activities/{id}/cancel"})
  public ResponseEntity<Map<String, String>> cancelActivity(@PathVariable long id) {
    return result(() -> service.cancelActivity(actor(), id));
  }

  @PostMapping({"/organizer/activities/{id}/sessions/{sessionId}/cancel",
      "/admin/activities/{id}/sessions/{sessionId}/cancel"})
  public ResponseEntity<Map<String, String>> cancelSession(@PathVariable long id,
      @PathVariable long sessionId) {
    return result(() -> service.cancelSession(actor(), id, sessionId));
  }

  @ExceptionHandler(ActivityException.class)
  public String readFailure(ActivityException ex, HttpServletResponse response, Model model) {
    response.setStatus(ex.status());
    model.addAttribute("message", ex.getMessage());
    return ex.status() == 403 ? "denied" : "error";
  }

  private ResponseEntity<Map<String, String>> result(Supplier<String> operation) {
    try {
      return ResponseEntity.ok(Map.of("message", operation.get()));
    } catch (ActivityException ex) {
      return ResponseEntity.status(ex.status()).body(Map.of("message", ex.getMessage()));
    }
  }

  private static long actor() {
    return AccountUser.current().getId();
  }
}
