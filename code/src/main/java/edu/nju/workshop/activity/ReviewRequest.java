package edu.nju.workshop.activity;

public record ReviewRequest(
    long activityId, Long sessionId, String name, String organizerName, ReviewKind kind) {

  public String reviewUrl() {
    String base = "/admin/reviews/" + activityId;
    return switch (kind) {
      case FIRST -> base;
      case REVISION -> base + "/revision";
      case SESSION -> base + "/sessions/" + sessionId;
    };
  }
}
