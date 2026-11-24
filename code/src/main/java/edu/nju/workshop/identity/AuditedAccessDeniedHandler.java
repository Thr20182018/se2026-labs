package edu.nju.workshop.identity;

import edu.nju.workshop.audit.OperationLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.util.NumberUtils;

/** 记录进入业务 Controller 之前被安全过滤器拒绝的关键写操作. */
@Component
public class AuditedAccessDeniedHandler implements AccessDeniedHandler {

  private static final Pattern ORGANIZER = Pattern.compile(
      "/organizer/activities(?:/([^/]+)(?:/(submit|revision)"
          + "|/(sessions)(?:/([^/]+)(?:/(submit))?)?)?)?");
  private static final Pattern REVIEW = Pattern.compile(
      "/admin/reviews/([^/]+)(?:/(revision)|/sessions/([^/]+))?");
  private static final Pattern REGISTRATION = Pattern.compile(
      "/me/sessions/([^/]+)/registrations(?:/([^/]+)/cancel)?");
  private static final Pattern CANCELLATION = Pattern.compile(
      "/(?:organizer|admin)/activities/([^/]+)(?:/sessions/([^/]+))?/cancel");
  private static final Pattern CAPACITY = Pattern.compile(
      "/organizer/activities/([^/]+)/sessions/([^/]+)/capacity");
  private static final Pattern CHECK_IN = Pattern.compile(
      "/organizer/activities/([^/]+)/sessions/([^/]+)/registrations/([^/]+)/check-in");
  private final OperationLogService logs;
  private final AccessDeniedHandlerImpl delegate = new AccessDeniedHandlerImpl();

  /** 配置被拒绝写操作的日志记录及拒绝访问页面. */
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
      String path = auditPath(request);
      Matcher organizer = ORGANIZER.matcher(path);
      Matcher review = REVIEW.matcher(path);
      Matcher registration = REGISTRATION.matcher(path);
      Matcher cancellation = CANCELLATION.matcher(path);
      Matcher capacity = CAPACITY.matcher(path);
      Matcher checkIn = CHECK_IN.matcher(path);
      String reason = exception instanceof CsrfException
          ? "安全令牌无效，请刷新页面后重试" : "当前角色无权执行该操作";
      if (registration.matches()) {
        String action = registration.group(2) != null ? "CANCEL_REGISTRATION"
            : "waitlist".equals(request.getParameter("action")) ? "JOIN_WAITLIST" : "REGISTER";
        logs.failure(user.getId(), null, id(registration.group(1)), id(registration.group(2)),
            action, reason);
      } else if (cancellation.matches()) {
        logs.failure(user.getId(), id(cancellation.group(1)), id(cancellation.group(2)),
            cancellation.group(2) == null ? "CANCEL_ACTIVITY" : "CANCEL_SESSION", reason);
      } else if (capacity.matches()) {
        logs.failure(user.getId(), id(capacity.group(1)), id(capacity.group(2)),
            "UPDATE_CAPACITY", reason);
      } else if (checkIn.matches()) {
        logs.failure(user.getId(), id(checkIn.group(1)), id(checkIn.group(2)),
            id(checkIn.group(3)), "CHECK_IN", reason);
      } else if (organizer.matches()) {
        logs.failure(user.getId(), id(organizer.group(1)), id(organizer.group(4)),
            organizerAction(organizer), reason);
      } else if (review.matches()) {
        String decision = request.getParameter("decision");
        String type = review.group(2) != null ? "REVISION"
            : review.group(3) != null ? "SESSION_REVIEW" : "REVIEW";
        String fallback = review.group(2) != null ? "REVIEW_REVISION"
            : review.group(3) != null ? "REVIEW_SESSION" : "REVIEW_ACTIVITY";
        String action = "approve".equals(decision) ? "APPROVE_" + type
            : "reject".equals(decision) ? "REJECT_" + type : fallback;
        logs.failure(user.getId(), id(review.group(1)), id(review.group(3)), action, reason);
      }
    }
    delegate.handle(request, response, exception);
  }

  private static String auditPath(HttpServletRequest request) {
    var parsed = RequestPath.parse(request.getRequestURI(), request.getContextPath());
    StringBuilder path = new StringBuilder();
    for (var element : parsed.pathWithinApplication().elements()) {
      if (element instanceof PathContainer.PathSegment segment) {
        // 与 MVC 一样逐段解码，段内编码斜线不能变成路由分隔符。
        String value = segment.valueToMatch();
        if (value.contains("/")) {
          return "";
        }
        path.append(value);
      } else {
        path.append(element.value());
      }
    }
    return path.toString();
  }

  private static String organizerAction(Matcher route) {
    if (route.group(1) == null) {
      return "CREATE_ACTIVITY";
    }
    if (route.group(2) != null) {
      return "revision".equals(route.group(2)) ? "SUBMIT_REVISION" : "SUBMIT_REVIEW";
    }
    if (route.group(5) != null) {
      return "SUBMIT_SESSION_REVIEW";
    }
    if (route.group(3) != null) {
      return route.group(4) == null ? "CREATE_SESSION" : "UPDATE_SESSION";
    }
    return "UPDATE_ACTIVITY";
  }

  private static Long id(String raw) {
    try {
      return raw == null ? null : NumberUtils.parseNumber(raw, Long.class);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
