package com.myfinance.saas.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A SaaS platform administrator (manages plans/pricing). Completely separate from finance-app
 * admins and from SaaS customers — this identity only grants access to /api/admin/** on the
 * SaaS backend.
 */
@Entity
@Table(name = "saas_admins", indexes = {
        @Index(name = "idx_saas_admin_email", columnList = "email", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaasAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash. Never logged or returned. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
