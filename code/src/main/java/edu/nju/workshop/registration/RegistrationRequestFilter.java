package edu.nju.workshop.registration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.util.NumberUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistrationRequestFilter extends OncePerRequestFilter {
  public static final String RECEIPT = RegistrationRequestFilter.class.getName() + ".receipt";
  private static final Pattern TARGET = Pattern.compile(
      "/me/sessions/([^/]+)/registrations(?:/[^/]+/cancel)?"
          + "|/organizer/activities/[^/]+/sessions/([^/]+)/capacity");
  private final RegistrationAdmission admission;

  public RegistrationRequestFilter(RegistrationAdmission admission) {
    this.admission = admission;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    Long sessionId = target(request);
    if (sessionId == null) {
      chain.doFilter(request, response);
      return;
    }
    var receipt = admission.begin(sessionId);
    request.setAttribute(RECEIPT, receipt);
    try {
      chain.doFilter(request, response);
    } finally {
      admission.end(receipt);
    }
  }

  private Long target(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod())) {
      return null;
    }
    StringBuilder path = new StringBuilder();
    for (var part : RequestPath.parse(request.getRequestURI(), request.getContextPath())
        .pathWithinApplication().elements()) {
      path.append(part instanceof PathContainer.PathSegment segment
          ? segment.valueToMatch() : part.value());
    }
    Matcher matcher = TARGET.matcher(path);
    try {
      return matcher.matches() ? NumberUtils.parseNumber(
          matcher.group(1) == null ? matcher.group(2) : matcher.group(1), Long.class) : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
