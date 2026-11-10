package edu.nju.workshop.shared;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SystemAppClock implements AppClock {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
