package edu.nju.workshop.shared;

import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageClock {
  private final AppClock clock;

  public PageClock(AppClock clock) {
    this.clock = clock;
  }

  @ModelAttribute("now")
  public LocalDateTime now() {
    return BeijingTime.toLocal(clock.now());
  }
}
