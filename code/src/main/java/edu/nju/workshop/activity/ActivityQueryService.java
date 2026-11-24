package edu.nju.workshop.activity;

import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 使用统一业务时间和只读事务提供工作坊分页查询. */
@Service
public class ActivityQueryService {

  private final ActivityQueryRepository repository;
  private final AppClock clock;

  /** 为公开场次查询提供数据访问和可注入的业务时钟. */
  public ActivityQueryService(ActivityQueryRepository repository, AppClock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * 筛选、截止和剩余名额使用同一次北京时间.
   * 只读事务让计数和分页看到同一份已提交数据。
   */
  @Transactional(readOnly = true)
  public QueryPage search(ActivityQuery query, int pageSize) {
    return search(query, pageSize, 0);
  }

  /** 在同一只读事务中查询场次，并按需定位选中场次所在页. */
  @Transactional(readOnly = true)
  public QueryPage search(ActivityQuery query, int pageSize, long selected) {
    LocalDateTime now = BeijingTime.toLocal(clock.now());
    return repository.search(query, now, pageSize, selected);
  }
}
