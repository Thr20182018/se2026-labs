package edu.nju.workshop.activity;

import java.time.LocalDateTime;
import java.util.List;

public record ManagedActivity(
    long id, String name, String description, long organizerId, String organizerName,
    PublicationStatus status, long reviewVersion, String pendingName, String pendingDescription,
    boolean revisionPending, PublicationStatus revisionStatus, List<ManagedSession> sessions) {

  public boolean editable() {
    return status == PublicationStatus.DRAFT || status == PublicationStatus.REJECTED;
  }

  public boolean published() {
    return status == PublicationStatus.PUBLISHED;
  }

  public boolean cancellable(LocalDateTime now) {
    return status != PublicationStatus.CANCELLED
        && sessions.stream().anyMatch(session -> session.endTime().isAfter(now));
  }
}
