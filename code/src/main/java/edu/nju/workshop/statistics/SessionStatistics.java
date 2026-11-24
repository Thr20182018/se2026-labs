package edu.nju.workshop.statistics;

import java.time.LocalDateTime;

/** 单个场次的活动信息、容量及报名到场统计. */
public record SessionStatistics(long activityId, String activityName, long sessionId,
    LocalDateTime startTime, LocalDateTime endTime, String organizerName, long capacity,
    StatisticsSummary summary) {
}
