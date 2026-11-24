package edu.nju.workshop.registration;

import edu.nju.workshop.shared.AppClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 在入口一起记录接收时间与在途请求，截止任务先等待这些请求结束. */
@Component
public class RegistrationAdmission {
  private final AppClock clock;
  private final Map<Long, List<Instant>> active = new HashMap<>();

  /** 使用统一业务时钟记录场次请求的接收时间. */
  public RegistrationAdmission(AppClock clock) {
    this.clock = clock;
  }

  /** 登记请求接收时间，并保留截止处理所需的在途凭据. */
  public synchronized Receipt begin(long sessionId) {
    Instant receivedAt = clock.now();
    active.computeIfAbsent(sessionId, id -> new ArrayList<>()).add(receivedAt);
    return new Receipt(sessionId, receivedAt);
  }

  /** 移除已完成请求的在途凭据. */
  public synchronized void end(Receipt receipt) {
    active.compute(receipt.sessionId(), (id, receipts) -> {
      receipts.remove(receipt.receivedAt());
      return receipts.isEmpty() ? null : receipts;
    });
  }

  /** 判断场次是否仍有截止前收到且尚未完成的请求. */
  public synchronized boolean hasPendingBefore(long sessionId, Instant deadline) {
    return active.getOrDefault(sessionId, List.of()).stream()
        .anyMatch(receivedAt -> receivedAt.isBefore(deadline));
  }

  /** 场次请求的接收凭据，用于截止资格判断及在途登记. */
  public record Receipt(long sessionId, Instant receivedAt) {
  }
}
