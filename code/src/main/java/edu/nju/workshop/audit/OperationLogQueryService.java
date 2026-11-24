package edu.nju.workshop.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.nju.workshop.activity.ActivityException;
import edu.nju.workshop.shared.ManagementScope;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogQueryService {

  public static final int PAGE_SIZE = 20;
  private final OperationLogQueryRepository repository;
  private final ObjectMapper json;

  public OperationLogQueryService(OperationLogQueryRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  /** 计数、分页和选中详情共享同一只读快照及权限条件。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page search(ManagementScope scope, LocalDateTime from, LocalDateTime to,
      String action, int requestedPage, Long selected) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ActivityException(400, "开始时间不能晚于结束时间");
    }
    if (requestedPage < 1) {
      throw new ActivityException(400, "页码必须大于零");
    }
    String filterAction = action == null ? "" : action.trim();
    if (!filterAction.isEmpty()) {
      try {
        OperationLogAction.valueOf(filterAction);
      } catch (IllegalArgumentException ex) {
        throw new ActivityException(400, "请选择有效的操作类型");
      }
    }
    long total = repository.count(scope, from, to, filterAction);
    int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    int page = Math.min(requestedPage, pages);
    List<OperationLogEntry> rows = repository.rows(scope, from, to, filterAction, page, PAGE_SIZE);
    OperationLogEntry detail = null;
    if (selected != null) {
      OperationLogEntry row = repository.selected(scope, from, to, filterAction, selected)
          .orElseThrow(() -> new ActivityException(404, "日志不存在或不在当前查询范围内"));
      detail = row.withValues(pretty(row.beforeValue()), pretty(row.afterValue()));
    }
    return new Page(rows, total, page, pages, detail);
  }

  private String pretty(String value) {
    if (value == null || value.isBlank()) {
      return "无记录";
    }
    try {
      return json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(value));
    } catch (JsonProcessingException ex) {
      return value;
    }
  }

  public record Page(List<OperationLogEntry> rows, long total, int page, int pages,
      OperationLogEntry selected) {
  }
}
