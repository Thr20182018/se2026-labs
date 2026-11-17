package edu.nju.workshop.registration;

public class RegistrationException extends RuntimeException {
  private final int status;
  private final RegistrationResult result;

  public RegistrationException(int status, RegistrationResult result) {
    super(result.message());
    this.status = status;
    this.result = result;
  }

  public int status() {
    return status;
  }

  public RegistrationResult result() {
    return result;
  }
}
