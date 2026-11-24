package edu.nju.workshop.attendance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期为已结束场次处理未签到的报名记录. */
@Component
@ConditionalOnProperty(name = "workshop.deadline-scheduling", matchIfMissing = true)
public class AttendanceDeadlineJob {
  private final AttendanceService service;

  /** 使用签到服务执行场次结束后的缺席转换. */
  public AttendanceDeadlineJob(AttendanceService service) {
    this.service = service;
  }

  /** 触发已结束场次的缺席状态更新. */
  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void markAbsent() {
    service.markEndedAbsent();
  }
}
