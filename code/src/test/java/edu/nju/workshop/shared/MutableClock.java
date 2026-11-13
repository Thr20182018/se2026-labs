package edu.nju.workshop.shared;

import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MutableClock implements AppClock {

  private volatile Instant current = Instant.parse("2026-11-10T02:00:00Z");

  public void setBeijing(LocalDateTime beijing) {
    current = beijing.atZone(BeijingTime.ZONE).toInstant();
  }

  @Override
  public Instant now() {
    return current;
  }
}
