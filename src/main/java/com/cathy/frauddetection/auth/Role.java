package com.cathy.frauddetection.auth;

/**
 * Application roles. One per user; see the users_role_check constraint in V5.
 *
 * <p>Constant names must match the SQL CHECK constraint values exactly.
 */
public enum Role {

    /** Reviews alerts and reads transactions. */
    ANALYST,

    /** Everything ANALYST can do, plus user management. */
    ADMIN;

    // Spring Security compares hasRole("ADMIN") against the string "ROLE_ADMIN".
    // Stored values stay unprefixed so they match the DB constraint; the prefix
    // is added here only, so no caller can forget it and silently get a 403.
    private static final String PREFIX = "ROLE_";

    public String authority() {
        return PREFIX + name();
    }
}
