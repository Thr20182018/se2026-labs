package edu.nju.workshop.attendance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "workshop.deadline-scheduling", matchIfMissing = true)
public class AttendanceDeadlineJob {
  private final AttendanceService service;

  public AttendanceDeadlineJob(AttendanceService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void markAbsent() {
    service.markEndedAbsent();
  }
}
