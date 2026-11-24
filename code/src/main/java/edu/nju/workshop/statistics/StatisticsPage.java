package edu.nju.workshop.statistics;

import java.util.List;

/** 报名名单分页及整个查询范围的人数汇总. */
public record StatisticsPage(List<StatisticsRow> rows, long total, int page, long pages,
    StatisticsSummary summary) {
}
