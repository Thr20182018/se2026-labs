package edu.nju.workshop.statistics;

import java.time.LocalDateTime;

public record SessionStatistics(long activityId, String activityName, long sessionId,
    LocalDateTime startTime, LocalDateTime endTime, String organizerName, long capacity,
    StatisticsSummary summary) {
}
