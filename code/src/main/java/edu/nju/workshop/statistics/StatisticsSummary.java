package edu.nju.workshop.statistics;

/** 统一口径的人数汇总，报名人数包含已签到和缺席记录. */
public record StatisticsSummary(long registered, long attended, long absent, long remaining) {
}
