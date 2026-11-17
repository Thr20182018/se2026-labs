package edu.nju.workshop.activity;

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
}
