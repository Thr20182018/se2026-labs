package edu.nju.workshop.shared;

import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 在测试中设置业务时间，覆盖截止、场次结束及并发等待边界. */
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
