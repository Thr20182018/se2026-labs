package edu.nju.workshop.activity;

import java.util.List;

public record QueryPage(List<SessionRow> rows, int total, int page, int pageSize) {

  public int pages() {
    if (total <= 0) {
      return 1;
    }
    return (total + pageSize - 1) / pageSize;
  }
}
