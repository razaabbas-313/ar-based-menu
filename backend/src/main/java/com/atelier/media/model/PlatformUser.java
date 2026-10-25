package com.atelier.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** SaaS platform operator (super admin). Separate from restaurant owner accounts. */
@Entity
@Table(name = "platform_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformUser {

    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    /** Not a valid BCrypt hash: the account exists but nobody can sign in until a password is set. */
    public static final String UNUSABLE_PASSWORD = "!";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Builder.Default
    @Column(name = "role", nullable = false, length = 30)
    private String role = ROLE_SUPER_ADMIN;

    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.TRUE;

    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 1;

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean hasUsablePassword() {
        return passwordHash != null && !UNUSABLE_PASSWORD.equals(passwordHash);
    }
}
