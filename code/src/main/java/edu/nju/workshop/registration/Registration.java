package edu.nju.workshop.registration;

public record Registration(long id, RegistrationStatus status, Integer waitlistOrder) {
}
