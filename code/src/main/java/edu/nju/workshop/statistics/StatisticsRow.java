package edu.nju.workshop.statistics;

import edu.nju.workshop.registration.RegistrationStatus;
import java.time.LocalDateTime;

public record StatisticsRow(long id, String participantName, String account, long activityId,
    String activityName, long sessionId, LocalDateTime startTime, LocalDateTime endTime,
    String organizerName, String organizerAccount, LocalDateTime registeredAt,
    RegistrationStatus status, LocalDateTime checkedInAt) {
}
