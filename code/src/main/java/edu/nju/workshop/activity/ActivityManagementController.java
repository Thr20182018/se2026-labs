package edu.nju.workshop.activity;

import edu.nju.workshop.identity.AccountUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ActivityManagementController {

  private final ActivityManagementService service;

  public ActivityManagementController(ActivityManagementService service) {
    this.service = service;
  }

  @GetMapping("/organizer/activities")
  public String activities(Model model) {
    model.addAttribute("activities", service.owned(actor()));
    model.addAttribute("draft", new ActivityDraft("", ""));
    return "organizer-activities";
  }

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

  @GetMapping("/organizer/activities/{id}")
  public String activity(@PathVariable long id, Model model) {
    ManagedActivity activity = service.ownedActivity(actor(), id);
    model.addAttribute("activity", activity);
    model.addAttribute("draft", new ActivityDraft(activity.name(), activity.description()));
    model.addAttribute("sessionDraft", new SessionDraft("", "", "", "", ""));
    model.addAttribute("editSessionId", 0L);
    return "organizer-activity";
  }

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

  @GetMapping("/admin/reviews")
  public String reviews(Model model) {
    model.addAttribute("activities", service.pending(actor()));
    return "admin-reviews";
  }

  @GetMapping("/admin/reviews/{id}")
  public String review(@PathVariable long id, Model model) {
    model.addAttribute("activity", service.forReview(actor(), id));
    return "admin-review";
  }

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
