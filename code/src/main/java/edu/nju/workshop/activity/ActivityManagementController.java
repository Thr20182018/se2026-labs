package edu.nju.workshop.activity;

import edu.nju.workshop.identity.AccountUser;
import edu.nju.workshop.registration.RegistrationAdmission;
import edu.nju.workshop.registration.RegistrationException;
import edu.nju.workshop.registration.RegistrationRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 提供组织者的活动编辑入口，以及管理员的首次发布和后续维护审核页面. */
@Controller
public class ActivityManagementController {

  private final ActivityManagementService service;
  private final PublishedActivityService published;

  /** 组合草稿管理与发布后维护服务，处理活动表单和审核请求. */
  public ActivityManagementController(ActivityManagementService service,
      PublishedActivityService published) {
    this.service = service;
    this.published = published;
  }

  /** 展示当前组织者的活动列表及新建活动表单. */
  @GetMapping("/organizer/activities")
  public String activities(Model model) {
    model.addAttribute("activities", service.owned(actor()));
    model.addAttribute("draft", new ActivityDraft("", ""));
    return "organizer-activities";
  }

  /** 创建活动草稿；校验失败时保留输入并返回活动列表. */
  @PostMapping("/organizer/activities")
  public String create(@RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String description, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    ActivityDraft draft = new ActivityDraft(name, description);
    try {
      long id = service.create(actor(), draft);
      return saved(id, "活动草稿已创建，可以继续添加场次。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activities(model);
        model.addAttribute("draft", draft);
      }
      return failure(ex, response, model, "organizer-activities");
    }
  }

  /** 展示本人活动详情，并准备活动、场次和发布后内容修改表单. */
  @GetMapping("/organizer/activities/{id}")
  public String activity(@PathVariable long id, Model model) {
    ManagedActivity activity = service.ownedActivity(actor(), id);
    model.addAttribute("activity", activity);
    model.addAttribute("draft", new ActivityDraft(activity.name(), activity.description()));
    model.addAttribute("sessionDraft", new SessionDraft("", "", "", "", ""));
    model.addAttribute("editSessionId", 0L);
    model.addAttribute("revisionDraft", new ActivityDraft(
        activity.revisionPending() ? activity.pendingName() : activity.name(),
        activity.revisionPending() ? activity.pendingDescription() : activity.description()));
    return "organizer-activity";
  }

  /** 保存活动草稿的名称和简介，失败时回填本次输入. */
  @PostMapping("/organizer/activities/{id}")
  public String edit(@PathVariable long id, @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String description, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    ActivityDraft draft = new ActivityDraft(name, description);
    try {
      service.edit(actor(), id, draft);
      return saved(id, "活动信息已保存。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activity(id, model);
        model.addAttribute("draft", draft);
      }
      return failure(ex, response, model, "organizer-activity");
    }
  }

  /** 根据是否提供场次编号创建或编辑场次，并保留失败表单的输入. */
  @PostMapping({"/organizer/activities/{id}/sessions",
      "/organizer/activities/{id}/sessions/{sessionId}"})
  public String saveSession(@PathVariable long id,
      @PathVariable(required = false) Long sessionId,
      @RequestParam(defaultValue = "") String startTime,
      @RequestParam(defaultValue = "") String endTime,
      @RequestParam(defaultValue = "") String deadline,
      @RequestParam(defaultValue = "") String location,
      @RequestParam(defaultValue = "") String capacity, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    SessionDraft draft = new SessionDraft(startTime, endTime, deadline, location, capacity);
    try {
      if (sessionId == null) {
        service.addSession(actor(), id, draft);
      } else {
        service.editSession(actor(), id, sessionId, draft);
      }
      return saved(id, "场次信息已保存。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activity(id, model);
        model.addAttribute("sessionDraft", draft);
        model.addAttribute("editSessionId", sessionId == null ? 0L : sessionId);
      }
      return failure(ex, response, model, "organizer-activity");
    }
  }

  /** 提交活动及其场次的首次发布申请，随后返回活动详情. */
  @PostMapping("/organizer/activities/{id}/submit")
  public String submit(@PathVariable long id, Model model, HttpServletResponse response,
      RedirectAttributes redirect) {
    try {
      service.submit(actor(), id);
      return saved(id, "已提交审核。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activity(id, model);
      }
      return failure(ex, response, model, "organizer-activity");
    }
  }

  /** 展示首次发布、内容修改和新增场次的统一待审核队列. */
  @GetMapping("/admin/reviews")
  public String reviews(Model model) {
    model.addAttribute("requests", published.pendingReviews(actor()));
    return "admin-reviews";
  }

  /** 将请求入口登记的接收时间用于容量调整，并返回 JSON 结果. */
  @PostMapping("/organizer/activities/{id}/sessions/{sessionId}/capacity")
  public ResponseEntity<Map<String, String>> capacity(@PathVariable long id,
      @PathVariable long sessionId, @RequestParam(defaultValue = "") String capacity,
      HttpServletRequest request) {
    var receipt = (RegistrationAdmission.Receipt)
        request.getAttribute(RegistrationRequestFilter.RECEIPT);
    try {
      return ResponseEntity.ok(Map.of("message", published.resizeCapacity(
          actor(), id, sessionId, capacity, receipt.receivedAt())));
    } catch (RegistrationException ex) {
      return ResponseEntity.status(ex.status()).body(Map.of("message", ex.getMessage()));
    }
  }

  /** 展示活动首次发布申请的审核详情. */
  @GetMapping("/admin/reviews/{id}")
  public String review(@PathVariable long id, Model model) {
    return reviewPage(id, null, "FIRST", model);
  }

  /** 按表单携带的审核版本处理首次发布申请，并回显结果. */
  @PostMapping("/admin/reviews/{id}")
  public String decide(@PathVariable long id, @RequestParam(defaultValue = "") String decision,
      @RequestParam(defaultValue = "-1") long reviewVersion, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    try {
      service.review(actor(), id, decision, reviewVersion);
      redirect.addFlashAttribute("notice", "approve".equals(decision) ? "审核通过，活动已发布。" : "已驳回申请。");
      return "redirect:/admin/reviews/" + id;
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        review(id, model);
      }
      return failure(ex, response, model, "admin-review");
    }
  }

  /** 提交已发布活动的内容修改申请，失败时保留待提交内容. */
  @PostMapping("/organizer/activities/{id}/revision")
  public String submitRevision(@PathVariable long id,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String description, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    ActivityDraft draft = new ActivityDraft(name, description);
    try {
      published.submitRevision(actor(), id, draft);
      return saved(id, "修改已提交审核，公开内容保持原样。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activity(id, model);
        model.addAttribute("revisionDraft", draft);
      }
      return failure(ex, response, model, "organizer-activity");
    }
  }

  /** 提交已发布活动中新增场次的独立审核申请. */
  @PostMapping("/organizer/activities/{id}/sessions/{sessionId}/submit")
  public String submitSession(@PathVariable long id, @PathVariable long sessionId,
      Model model, HttpServletResponse response, RedirectAttributes redirect) {
    try {
      published.submitSession(actor(), id, sessionId);
      return saved(id, "场次已提交审核。", redirect);
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        activity(id, model);
      }
      return failure(ex, response, model, "organizer-activity");
    }
  }

  /** 展示活动内容修改申请的审核详情. */
  @GetMapping("/admin/reviews/{id}/revision")
  public String revision(@PathVariable long id, Model model) {
    return reviewPage(id, null, "REVISION", model);
  }

  /** 展示指定新增场次的审核详情. */
  @GetMapping("/admin/reviews/{id}/sessions/{sessionId}")
  public String sessionReview(@PathVariable long id, @PathVariable long sessionId, Model model) {
    return reviewPage(id, sessionId, "SESSION", model);
  }

  /** 按请求路径处理内容修改或新增场次审核，通过后返回审核队列. */
  @PostMapping({"/admin/reviews/{id}/revision",
      "/admin/reviews/{id}/sessions/{sessionId}"})
  public String decideMaintenance(@PathVariable long id,
      @PathVariable(required = false) Long sessionId,
      @RequestParam(defaultValue = "") String decision,
      @RequestParam(defaultValue = "-1") long reviewVersion, Model model,
      HttpServletResponse response, RedirectAttributes redirect) {
    String kind = sessionId == null ? "REVISION" : "SESSION";
    try {
      if (sessionId == null) {
        published.reviewRevision(actor(), id, decision, reviewVersion);
      } else {
        published.reviewSession(actor(), id, sessionId, decision, reviewVersion);
      }
      redirect.addFlashAttribute("notice", "approve".equals(decision) ? "审核通过，内容已生效。" : "已驳回申请。");
      return "redirect:/admin/reviews";
    } catch (ActivityException ex) {
      if (ex.status() != 403) {
        reviewPage(id, sessionId, kind, model);
      }
      return failure(ex, response, model, "admin-review");
    }
  }

  private String reviewPage(long id, Long sessionId, String kind, Model model) {
    ManagedActivity activity = service.forReview(actor(), id);
    ManagedSession session = sessionId == null ? null : activity.sessions().stream()
        .filter(item -> item.id() == sessionId).findFirst()
        .orElseThrow(() -> new ActivityException(404, "场次不存在"));
    boolean canReview = switch (kind) {
      case "FIRST" -> activity.status() == PublicationStatus.PENDING;
      case "REVISION" -> activity.published() && activity.revisionPending();
      default -> activity.published() && session.status() == PublicationStatus.PENDING;
    };
    String suffix = sessionId != null ? "/sessions/" + sessionId
        : "REVISION".equals(kind) ? "/revision" : "";
    model.addAttribute("activity", activity);
    model.addAttribute("reviewKind", kind);
    model.addAttribute("reviewSession", session);
    model.addAttribute("reviewAction", "/admin/reviews/" + id + suffix);
    model.addAttribute("reviewVersion", session == null
        ? activity.reviewVersion() : session.reviewVersion());
    model.addAttribute("canReview", canReview);
    return "admin-review";
  }

  /** 将活动读取失败转换为业务状态码及权限拒绝或错误页面. */
  @ExceptionHandler(ActivityException.class)
  public String readFailure(ActivityException ex, HttpServletResponse response, Model model) {
    return failure(ex, response, model, "error");
  }

  private static long actor() {
    return AccountUser.current().getId();
  }

  private static String saved(long id, String notice, RedirectAttributes redirect) {
    redirect.addFlashAttribute("notice", notice);
    return "redirect:/organizer/activities/" + id;
  }

  private static String failure(ActivityException ex, HttpServletResponse response,
      Model model, String page) {
    response.setStatus(ex.status());
    model.addAttribute("message", ex.getMessage());
    return ex.status() == 403 ? "denied" : page;
  }
}
