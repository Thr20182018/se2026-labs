package edu.nju.workshop.registration;

/** 报名记录的当前状态及候补排序信息. */
public record Registration(long id, RegistrationStatus status, Integer waitlistOrder) {
}
