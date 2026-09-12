package com.cathy.frauddetection.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Persistent authentication principal, mapping the users table from V5.
 *
 * <p>Named UserAccount rather than User: Spring Security ships its own User
 * class, and having both in scope invites the wrong import.
 *
 * <p>Deliberately does not implement UserDetails. Persistence shape and
 * security contract are separate concerns; the adapter lives in
 * AppUserDetailsService.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    // IDENTITY matches BIGSERIAL: the database assigns the id on insert.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    // STRING, never the ORDINAL default: ordinals store positions, so inserting
    // a constant into the middle of the enum silently reinterprets stored rows.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    // Written by the column's DEFAULT NOW(); read-only here so there is one
    // source of truth for creation time.
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Required by JPA; protected so application code cannot build a blank instance. */
    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
