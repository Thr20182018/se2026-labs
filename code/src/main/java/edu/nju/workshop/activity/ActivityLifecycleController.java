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

/** 提供管理员活动浏览，以及组织者和管理员的取消操作入口. */
@Controller
public class ActivityLifecycleController {
  private final ActivityLifecycleService service;

  /** 使用活动生命周期服务处理管理查询和取消请求. */
  public ActivityLifecycleController(ActivityLifecycleService service) {
    this.service = service;
  }

  /** 展示管理员可查看的全平台活动列表. */
  @GetMapping("/admin/activities")
  public String activities(Model model) {
    model.addAttribute("activities", service.all(actor()));
    return "admin-activities";
  }

  /** 展示管理员的活动详情及所属场次. */
  @GetMapping("/admin/activities/{id}")
  public String activity(@PathVariable long id, Model model) {
    model.addAttribute("activity", service.forAdmin(actor(), id));
    return "admin-activity";
  }

  /** 取消活动并返回联动处理未结束场次的结果. */
  @PostMapping({"/organizer/activities/{id}/cancel", "/admin/activities/{id}/cancel"})
  public ResponseEntity<Map<String, String>> cancelActivity(@PathVariable long id) {
    return result(() -> service.cancelActivity(actor(), id));
  }

  /** 取消指定场次并以 JSON 返回业务结果. */
  @PostMapping({"/organizer/activities/{id}/sessions/{sessionId}/cancel",
      "/admin/activities/{id}/sessions/{sessionId}/cancel"})
  public ResponseEntity<Map<String, String>> cancelSession(@PathVariable long id,
      @PathVariable long sessionId) {
    return result(() -> service.cancelSession(actor(), id, sessionId));
  }

  /** 将读取活动时的业务拒绝转换为对应状态码和错误页面. */
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
