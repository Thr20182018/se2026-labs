package edu.nju.workshop.registration;

import edu.nju.workshop.shared.AppClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 在入口一起记录接收时间与在途请求，截止任务先等待这些请求结束。 */
@Component
public class RegistrationAdmission {
  private final AppClock clock;
  private final Map<Long, List<Instant>> active = new HashMap<>();

  public RegistrationAdmission(AppClock clock) {
    this.clock = clock;
  }

  public synchronized Receipt begin(long sessionId) {
    Instant receivedAt = clock.now();
    active.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(receivedAt);
    return new Receipt(sessionId, receivedAt);
  }

  public synchronized void end(Receipt receipt) {
    active.compute(receipt.sessionId(), (id, receipts) -> {
      receipts.remove(receipt.receivedAt());
      return receipts.isEmpty() ? null : receipts;
    });
  }

  public synchronized boolean hasPendingBefore(long sessionId, Instant deadline) {
    return active.getOrDefault(sessionId, List.of()).stream()
        .anyMatch(receivedAt -> receivedAt.isBefore(deadline));
  }

  public record Receipt(long sessionId, Instant receivedAt) {
  }
}
