package com.myfinance.model;

import com.myfinance.model.enums.levetf.AlertTrigger;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A user's alert preference for a trigger (optionally scoped to a strategy). In-app is the primary
 * channel (always available); email is optional and only sent when SMTP is configured by the operator.
 *
 * <p>Additive table {@code lev_etf_alert_prefs}; new user-owned entity with {@code userId}.
 */
@Entity
@Table(name = "lev_etf_alert_prefs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfAlertPref {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    /** Null = applies to all strategies. */
    private Long strategyId;

    @Enumerated(EnumType.STRING)
    private AlertTrigger trigger;

    /** e.g. drawdown/gap threshold; null when the trigger has no threshold. */
    private BigDecimal threshold;

    @Builder.Default
    private Boolean inAppEnabled = true;
    @Builder.Default
    private Boolean emailEnabled = false;

    /** "ONCE" (fire once until reset) or "REPEAT". */
    @Builder.Default
    private String repeatPolicy = "ONCE";

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
