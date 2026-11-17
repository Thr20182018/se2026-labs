package edu.nju.workshop.activity;

import java.time.LocalDateTime;

public record ManagedSession(
    long id, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime deadline,
    String location, int capacity, PublicationStatus status, long reviewVersion) {

  public boolean editable() {
    return status == PublicationStatus.DRAFT || status == PublicationStatus.REJECTED;
  }
}
