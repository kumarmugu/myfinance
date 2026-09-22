package com.myfinance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.myfinance.model.enums.Broker;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stored broker API credentials for one user's account, encrypted at rest.
 *
 * <p>Secret fields ({@code secret1}/{@code secret2}) hold AES-GCM ciphertext produced by
 * {@code CredentialCipher} — never plaintext — and are {@link JsonIgnore}d so they can never be
 * serialized into an API response. Non-secret identifiers (e.g. the IBKR Query ID, Tiger ID) are
 * kept in clear metadata columns; they are identifiers, not secrets.
 *
 * <p>Field mapping per broker (only the minimum needed is stored):
 * <ul>
 *   <li><b>IBKR</b>: meta1 = Flex Query ID; secret1 = Flex token.</li>
 *   <li><b>TIGER</b>: meta1 = Tiger ID; meta2 = Tiger account (optional); secret1 = RSA private key.</li>
 *   <li><b>SAXO</b>: meta1 = environment (sim/live); secret1 = refresh/access token (planned).</li>
 * </ul>
 *
 * <p>Unique per (userId, accountId, broker). Additive/prod-safe: brand-new nullable-friendly table.
 */
@Entity
@Table(name = "broker_credentials",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "account_id", "broker"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BrokerCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user (tenant boundary). */
    @Column(nullable = false)
    private Long userId;

    /** The owner and account this credential syncs into. */
    private Long ownerId;
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Broker broker;

    /** Non-secret identifier #1 (IBKR Query ID / Tiger ID / Saxo environment). */
    @Column(length = 256)
    private String meta1;

    /** Non-secret identifier #2 (e.g. Tiger account). */
    @Column(length = 256)
    private String meta2;

    /** Encrypted secret #1 (Flex token / Tiger RSA private key / Saxo token). NEVER serialized. */
    @JsonIgnore
    @Column(columnDefinition = "CLOB")
    private String secret1;

    /** Encrypted secret #2, reserved for brokers needing a second secret. NEVER serialized. */
    @JsonIgnore
    @Column(columnDefinition = "CLOB")
    private String secret2;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
