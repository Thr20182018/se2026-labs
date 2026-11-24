package edu.nju.workshop.statistics;

import edu.nju.workshop.registration.RegistrationStatus;
import java.time.LocalDateTime;

/** 统计名单与导出使用的参与者、活动、场次及报名状态数据. */
public record StatisticsRow(long id, String participantName, String account, long activityId,
    String activityName, long sessionId, LocalDateTime startTime, LocalDateTime endTime,
    String organizerName, String organizerAccount, LocalDateTime registeredAt,
    RegistrationStatus status, LocalDateTime checkedInAt) {
}
