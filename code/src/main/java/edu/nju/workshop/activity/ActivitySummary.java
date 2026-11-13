package edu.nju.workshop.activity;

public record ActivitySummary(
    long id, String name, PublicationStatus status, int sessionCount, String organizerName) {
}
