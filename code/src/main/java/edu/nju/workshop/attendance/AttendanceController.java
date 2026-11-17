package edu.nju.workshop.attendance;

import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.identity.AccountUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AttendanceController {
  private final AttendanceService service;

  public AttendanceController(AttendanceService service) {
    this.service = service;
  }

  @GetMapping("/organizer/activities/{id}/sessions/{sessionId}/attendance")
  public String attendance(@PathVariable long id, @PathVariable long sessionId, Model model) {
    var page = service.attendance(AccountUser.current().getId(), id, sessionId);
    model.addAttribute("activity", page.activity());
    model.addAttribute("workshopSession", page.session());
    model.addAttribute("attendees", page.attendees());
    return "attendance";
  }

  @PostMapping("/organizer/activities/{id}/sessions/{sessionId}"
      + "/registrations/{registrationId}/check-in")
  public ResponseEntity<Map<String, String>> checkIn(@PathVariable long id,
      @PathVariable long sessionId, @PathVariable long registrationId) {
    try {
      return ResponseEntity.ok(Map.of("message",
          service.checkIn(AccountUser.current().getId(), id, sessionId, registrationId)));
    } catch (ActivityException ex) {
      return ResponseEntity.status(ex.status()).body(Map.of("message", ex.getMessage()));
    }
  }

  @ExceptionHandler(ActivityException.class)
  public String readFailure(ActivityException ex, HttpServletResponse response, Model model) {
    response.setStatus(ex.status());
    model.addAttribute("message", ex.getMessage());
    return ex.status() == 403 ? "denied" : "error";
  }
}
