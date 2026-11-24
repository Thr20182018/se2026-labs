package edu.nju.workshop.registration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 本人报名列表中的活动、场次和当前申请状态. */
public record RegistrationRow(long id, long sessionId, String name, String location,
    LocalDateTime start, LocalDateTime end, LocalDateTime registeredAt,
    RegistrationStatus status, Integer waitlistOrder, LocalDateTime deadline, boolean available) {

  /** 判断当前时间是否仍允许取消报名或退出候补. */
  public boolean cancellable(LocalDateTime now) {
    return available && now.isBefore(deadline) && now.isBefore(end)
        && (status == RegistrationStatus.REGISTERED || status == RegistrationStatus.WAITLIST);
  }

  /** 格式化场次的开始和结束时间供列表展示. */
  public String timeLabel() {
    DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    return start.format(format) + " → " + end.format(format);
  }
}
