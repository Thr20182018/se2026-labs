package edu.nju.workshop.identity;

import java.io.Serializable;

/** 持久化账号身份，包含认证所需的密码哈希和单一角色. */
public record UserAccount(
    long id,
    String account,
    String passwordHash,
    String displayName,
    Role role) implements Serializable {
}
