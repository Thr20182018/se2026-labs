package edu.nju.workshop.registration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record RegistrationRow(long id, long sessionId, String name, String location,
    LocalDateTime start, LocalDateTime end, LocalDateTime registeredAt,
    RegistrationStatus status, Integer waitlistOrder, LocalDateTime deadline, boolean available) {

  public boolean cancellable(LocalDateTime now) {
    return available && now.isBefore(deadline) && now.isBefore(end)
        && (status == RegistrationStatus.REGISTERED || status == RegistrationStatus.WAITLIST);
  }

  public String timeLabel() {
    DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    return start.format(format) + " → " + end.format(format);
  }
}
