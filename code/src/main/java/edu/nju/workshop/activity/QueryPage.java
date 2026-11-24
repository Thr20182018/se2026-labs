package edu.nju.workshop.activity;

import java.util.List;

/** 工作坊查询的当前页场次、匹配总数及分页信息. */
public record QueryPage(List<SessionRow> rows, int total, int page, int pageSize) {

  /** 计算分页总数，空结果仍保留第一页. */
  public int pages() {
    if (total <= 0) {
      return 1;
    }
    return (total + pageSize - 1) / pageSize;
  }
}
