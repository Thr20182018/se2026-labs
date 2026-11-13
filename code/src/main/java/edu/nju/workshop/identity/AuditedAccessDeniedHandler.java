package edu.nju.workshop.identity;

import edu.nju.workshop.audit.OperationLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 记录进入业务 Controller 之前被安全过滤器拒绝的关键写操作。 */
@Component
public class AuditedAccessDeniedHandler implements AccessDeniedHandler {

  private static final Pattern ORGANIZER = Pattern.compile(
      "/organizer/activities(?:/(\\d+)(?:/(submit)|/(sessions)(?:/(\\d+))?)?)?");
  private static final Pattern REVIEW = Pattern.compile("/admin/reviews/(\\d+)");
  private final OperationLogService logs;
  private final AccessDeniedHandlerImpl delegate = new AccessDeniedHandlerImpl();

  public AuditedAccessDeniedHandler(OperationLogService logs) {
    this.logs = logs;
    delegate.setErrorPage("/access-denied");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException exception) throws IOException, ServletException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if ("POST".equals(request.getMethod()) && authentication != null
        && authentication.getPrincipal() instanceof AccountUser user) {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      Matcher organizer = ORGANIZER.matcher(path);
      Matcher review = REVIEW.matcher(path);
      String reason = exception instanceof CsrfException
          ? "安全令牌无效，请刷新页面后重试" : "当前角色无权执行该操作";
      if (organizer.matches()) {
        logs.failure(user.getId(), id(organizer.group(1)), id(organizer.group(4)),
            organizerAction(organizer), reason);
      } else if (review.matches()) {
        String decision = request.getParameter("decision");
        String action = "approve".equals(decision) ? "APPROVE_REVIEW"
            : "reject".equals(decision) ? "REJECT_REVIEW" : "REVIEW_ACTIVITY";
        logs.failure(user.getId(), id(review.group(1)), null, action, reason);
      }
    }
    delegate.handle(request, response, exception);
  }

  private static String organizerAction(Matcher route) {
    if (route.group(1) == null) {
      return "CREATE_ACTIVITY";
    }
    if (route.group(2) != null) {
      return "SUBMIT_REVIEW";
    }
    if (route.group(3) != null) {
      return route.group(4) == null ? "CREATE_SESSION" : "UPDATE_SESSION";
    }
    return "UPDATE_ACTIVITY";
  }

  private static Long id(String raw) {
    try {
      return raw == null ? null : Long.valueOf(raw);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
