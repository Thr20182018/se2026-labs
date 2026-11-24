package edu.nju.workshop.statistics;

import java.util.List;

public record StatisticsPage(List<StatisticsRow> rows, long total, int page, long pages,
    StatisticsSummary summary) {
}
