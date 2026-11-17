package edu.nju.workshop.registration;

import edu.nju.workshop.activity.Availability;

public record RegistrationResult(String message, Long registrationId, String status,
    String statusLabel, int remaining, String availability, String availabilityLabel,
    String action, String actionLabel, boolean canApply) {

  public static RegistrationResult of(String message, Registration registration,
      int remaining, Availability availability) {
    boolean waiting = availability == Availability.WAITLIST;
    String label = registration == null ? "" : registration.status().getLabel();
    String actionLabel = registration != null
        ? registration.status() == RegistrationStatus.REGISTERED ? "已报名" : label
        : switch (availability) {
      case OPEN -> "报名此场次";
      case WAITLIST -> "加入候补";
      case CLOSED -> "报名已截止";
      case CANCELLED -> "场次已取消";
    };
    return new RegistrationResult(message, registration == null ? null : registration.id(),
        registration == null ? null : registration.status().name(), label, remaining,
        availability.name(), availability.getLabel(), waiting ? "waitlist" : "register",
        actionLabel, registration == null && (availability == Availability.OPEN || waiting));
  }
}
