package edu.nju.workshop.registration;

import edu.nju.workshop.identity.AccountUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RegistrationController {
  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @GetMapping("/me/registrations")
  public String mine(Model model) {
    model.addAttribute("registrations", service.mine(AccountUser.current().getId()));
    return "my-registrations";
  }

  @PostMapping("/me/sessions/{sessionId}/registrations")
  @ResponseBody
  public RegistrationResult apply(@PathVariable long sessionId,
      @RequestParam(defaultValue = "") String action, HttpServletRequest request) {
    var receipt = (RegistrationAdmission.Receipt)
        request.getAttribute(RegistrationRequestFilter.RECEIPT);
    return service.apply(AccountUser.current().getId(), sessionId, action, receipt.receivedAt());
  }

  @ExceptionHandler(RegistrationException.class)
  public ResponseEntity<RegistrationResult> failure(RegistrationException ex) {
    return ResponseEntity.status(ex.status()).body(ex.result());
  }
}
