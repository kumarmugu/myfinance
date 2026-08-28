package com.myfinance.saas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Records provider webhook events that have been processed, keyed by the provider's unique
 * event id. Enables idempotent webhook handling: duplicate deliveries are safe no-ops.
 */
@Entity
@Table(name = "processed_webhook_events", indexes = {
        @Index(name = "idx_webhook_event_id", columnList = "eventId", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Provider event id (e.g. Stripe evt_...). Unique. */
    @Column(nullable = false, unique = true)
    private String eventId;

    private String eventType;

    @Column(updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() { processedAt = LocalDateTime.now(); }
}
