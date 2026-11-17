package edu.nju.workshop.activity;

public record ActivitySummary(
    long id, String name, PublicationStatus status, int sessionCount, String organizerName,
    PublicationStatus revisionStatus, int newSessionsPending, int newSessionsRejected,
    int newSessionsPublished) {

  public boolean hasNewSessionReviews() {
    return newSessionsPending + newSessionsRejected + newSessionsPublished > 0;
  }
}
