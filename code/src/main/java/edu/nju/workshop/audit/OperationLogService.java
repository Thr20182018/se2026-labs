package edu.nju.workshop.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.nju.workshop.shared.AppClock;
import edu.nju.workshop.shared.BeijingTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperationLogService {

  private final OperationLogRepository repository;
  private final ObjectMapper json;
  private final AppClock clock;
  private final TransactionTemplate failureTransaction;

  public OperationLogService(OperationLogRepository repository, ObjectMapper json, AppClock clock,
      PlatformTransactionManager transactions) {
    this.repository = repository;
    this.json = json;
    this.clock = clock;
    this.failureTransaction = new TransactionTemplate(transactions);
    failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** 成功日志使用调用方的业务事务，自动发布用空 actorId 表示系统。 */
  public void success(Long actorId, Long activityId, Long sessionId, String action,
      Object before, Object after) {
    insert(actorId, activityId, sessionId, action, "SUCCESS", null, before, after);
  }

  /** 由业务入口在回滚后调用，失败原因单独提交。 */
  public void failure(long actorId, Long activityId, Long sessionId, String action, String reason) {
    failureTransaction.executeWithoutResult(status ->
        insert(actorId, activityId, sessionId, action, "FAILURE", reason, null, null));
  }

  private void insert(Long actorId, Long activityId, Long sessionId, String action,
      String result, String reason, Object before, Object after) {
    repository.insert(BeijingTime.toLocal(clock.now()), actorId, activityId, sessionId,
        action, result, reason, encode(before), encode(after));
  }

  private String encode(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("无法保存操作记录", ex);
    }
  }
}
