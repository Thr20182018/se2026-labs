package edu.nju.workshop.attendance;

import edu.nju.workshop.registration.RegistrationStatus;
import java.time.LocalDateTime;

public record AttendanceRow(long id, String participantName, String account,
    RegistrationStatus status, LocalDateTime checkedInAt) {
}
