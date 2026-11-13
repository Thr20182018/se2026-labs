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

/** 记录进入业务 Controller 之前被安全过滤器拒绝的关键写操作。 */
@Component
public class AuditedAccessDeniedHandler implements AccessDeniedHandler {

  private static final Pattern ORGANIZER = Pattern.compile(
      "/organizer/activities(?:/([^/]+)(?:/(submit)|/(sessions)(?:/([^/]+))?)?)?");
  private static final Pattern REVIEW = Pattern.compile("/admin/reviews/([^/]+)");
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
      String path = auditPath(request);
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
      return "SUBMIT_REVIEW";
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
