package com.myfinance.saas.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single-use, time-limited token for email verification or password reset.
 *
 * Only a SHA-256 hash of the token is stored — the raw token is delivered to the customer
 * (via email) and never persisted, so a database leak cannot reveal usable tokens.
 */
@Entity
@Table(name = "one_time_tokens", indexes = {
        @Index(name = "idx_ott_hash", columnList = "tokenHash", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OneTimeToken {

    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
