package edu.nju.workshop.shared;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 在非测试环境中以系统时钟提供当前业务时刻. */
@Component
@Profile("!test")
public class SystemAppClock implements AppClock {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
