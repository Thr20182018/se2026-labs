package edu.nju.workshop.shared;

import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 为页面模板统一提供业务时钟对应的北京时间. */
@ControllerAdvice
public class PageClock {
  private final AppClock clock;

  /** 使用统一业务时钟生成页面时间. */
  public PageClock(AppClock clock) {
    this.clock = clock;
  }

  /** 提供页面状态和操作按钮判断所需的当前北京时间. */
  @ModelAttribute("now")
  public LocalDateTime now() {
    return BeijingTime.toLocal(clock.now());
  }
}
