package edu.nju.workshop.registration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期触发报名截止后的剩余候补关闭. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "workshop.deadline-scheduling", matchIfMissing = true)
public class RegistrationDeadlineJob {
  private final RegistrationService service;

  /** 使用报名服务处理到期候补队列. */
  public RegistrationDeadlineJob(RegistrationService service) {
    this.service = service;
  }

  /** 触发已到截止时间且无合格在途请求的候补关闭. */
  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void closeWaitlists() {
    service.closeDueWaitlists();
  }
}
