package edu.nju.workshop.registration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "workshop.deadline-scheduling", matchIfMissing = true)
public class RegistrationDeadlineJob {
  private final RegistrationService service;

  public RegistrationDeadlineJob(RegistrationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void closeWaitlists() {
    service.closeDueWaitlists();
  }
}
