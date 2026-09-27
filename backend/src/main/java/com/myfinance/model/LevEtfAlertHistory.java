package com.myfinance.model;

import com.myfinance.model.enums.levetf.AlertTrigger;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A fired alert, surfaced in-app (the primary channel) and optionally emailed. {@code dedupeKey}
 * prevents re-sending the same threshold alert unless the condition exits and re-enters (or the user
 * chose REPEAT).
 *
 * <p>Additive table {@code lev_etf_alert_history}; new user-owned entity with {@code userId}. Unique
 * on (userId, dedupeKey) to enforce deduplication.
 */
@Entity
@Table(name = "lev_etf_alert_history", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "dedupe_key"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LevEtfAlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private Long strategyId;

    @Enumerated(EnumType.STRING)
    private AlertTrigger trigger;

    private BigDecimal threshold;

    @Column(length = 1000)
    private String message;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Builder.Default
    private Boolean channelInApp = true;
    @Builder.Default
    private Boolean channelEmail = false;
    /** "PENDING", "SENT", "SKIPPED_DISABLED", "FAILED". */
    private String emailDeliveryStatus;

    @Builder.Default
    private Boolean readFlag = false;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
