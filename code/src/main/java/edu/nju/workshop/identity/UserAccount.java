package edu.nju.workshop.identity;

import java.io.Serializable;

public record UserAccount(
    long id,
    String account,
    String passwordHash,
    String displayName,
    Role role) implements Serializable {
}
