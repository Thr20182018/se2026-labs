package edu.nju.workshop.attendance;

import edu.nju.workshop.registration.RegistrationStatus;
import java.time.LocalDateTime;

/** 签到名单中的参与者身份、报名状态和签到时间. */
public record AttendanceRow(long id, String participantName, String account,
    RegistrationStatus status, LocalDateTime checkedInAt) {
}
