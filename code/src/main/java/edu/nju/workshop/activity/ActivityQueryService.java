package edu.nju.workshop.activity;

import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityQueryService {

  private final ActivityQueryRepository repository;
  private final AppClock clock;

  public ActivityQueryService(ActivityQueryRepository repository, AppClock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * 筛选、截止和剩余名额使用同一次北京时间。
   * 只读事务让计数和分页看到同一份已提交数据。
   */
  @Transactional(readOnly = true)
  public QueryPage search(ActivityQuery query, int pageSize) {
    LocalDateTime now = BeijingTime.toLocal(clock.now());
    return repository.search(query, now, pageSize);
  }
}
