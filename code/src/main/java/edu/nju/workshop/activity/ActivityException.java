package edu.nju.workshop.activity;

public class ActivityException extends RuntimeException {

  private final int status;

  public ActivityException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
