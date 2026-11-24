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

/** 提供参与者报名、候补、取消及本人报名列表入口. */
@Controller
public class RegistrationController {
  private final RegistrationService service;

  /** 使用报名服务处理参与者请求. */
  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  /** 展示当前参与者的报名和候补记录. */
  @GetMapping("/me/registrations")
  public String mine(Model model) {
    model.addAttribute("registrations", service.mine(AccountUser.current().getId()));
    return "my-registrations";
  }

  /** 按请求入口记录的接收时间提交报名或候补申请. */
  @PostMapping("/me/sessions/{sessionId}/registrations")
  @ResponseBody
  public RegistrationResult apply(@PathVariable long sessionId,
      @RequestParam(defaultValue = "") String action, HttpServletRequest request) {
    var receipt = (RegistrationAdmission.Receipt)
        request.getAttribute(RegistrationRequestFilter.RECEIPT);
    return service.apply(AccountUser.current().getId(), sessionId, action, receipt.receivedAt());
  }

  /** 将报名业务失败转换为相应状态码和页面反馈数据. */
  @ExceptionHandler(RegistrationException.class)
  public ResponseEntity<RegistrationResult> failure(RegistrationException ex) {
    return ResponseEntity.status(ex.status()).body(ex.result());
  }

  /** 按请求接收时间取消本人报名或退出候补. */
  @PostMapping("/me/sessions/{sessionId}/registrations/{registrationId}/cancel")
  @ResponseBody
  public RegistrationResult cancel(@PathVariable long sessionId, @PathVariable long registrationId,
      HttpServletRequest request) {
    var receipt = (RegistrationAdmission.Receipt)
        request.getAttribute(RegistrationRequestFilter.RECEIPT);
    return service.cancel(AccountUser.current().getId(), sessionId, registrationId,
        receipt.receivedAt());
  }
}
