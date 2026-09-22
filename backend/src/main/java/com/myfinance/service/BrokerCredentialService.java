package com.myfinance.service;

import com.myfinance.model.BrokerCredential;
import com.myfinance.model.enums.Broker;
import com.myfinance.repository.BrokerCredentialRepository;
import com.myfinance.security.CredentialCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages stored broker API credentials: encrypts secrets on write (via {@link CredentialCipher}),
 * exposes only masked status to callers, and decrypts secrets solely for the sync services at call
 * time. Plaintext secrets never leave this service and are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerCredentialService {

    private final BrokerCredentialRepository repository;
    private final CredentialCipher cipher;

    /** Whether credential storage is available (a master key is configured). */
    public boolean encryptionEnabled() { return cipher.isEnabled(); }

    /** Non-secret, maskable view of a stored credential for the UI. */
    public record CredentialStatus(Long id, Broker broker, Long ownerId, Long accountId,
                                   String meta1, boolean secret1Set, boolean secret2Set,
                                   String updatedAt) {}

    public List<CredentialStatus> listStatuses(Long userId) {
        return repository.findByUserId(userId).stream().map(this::toStatus).toList();
    }

    public Optional<CredentialStatus> statusFor(Long userId, Long accountId, Broker broker) {
        return repository.findByUserIdAndAccountIdAndBroker(userId, accountId, broker).map(this::toStatus);
    }

    private CredentialStatus toStatus(BrokerCredential c) {
        return new CredentialStatus(c.getId(), c.getBroker(), c.getOwnerId(), c.getAccountId(),
                c.getMeta1(), c.getSecret1() != null, c.getSecret2() != null,
                c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
    }

    /**
     * Create or update the credential for (user, account, broker). Secrets are encrypted before
     * saving; a null/blank secret leaves the previously-stored value unchanged (so the user can
     * update the Query ID without re-entering the token). Non-secret metadata is stored as-is.
     */
    @Transactional
    public CredentialStatus save(Long userId, Long ownerId, Long accountId, Broker broker,
                                 String meta1, String meta2, String secret1, String secret2) {
        if (!cipher.isEnabled()) {
            throw new IllegalStateException("Credential encryption is not configured on the server");
        }
        BrokerCredential c = repository.findByUserIdAndAccountIdAndBroker(userId, accountId, broker)
                .orElseGet(() -> BrokerCredential.builder().userId(userId).accountId(accountId).broker(broker).build());
        c.setOwnerId(ownerId);
        c.setMeta1(meta1);
        c.setMeta2(meta2);
        if (secret1 != null && !secret1.isBlank()) c.setSecret1(cipher.encrypt(secret1));
        if (secret2 != null && !secret2.isBlank()) c.setSecret2(cipher.encrypt(secret2));
        BrokerCredential saved = repository.save(c);
        // Log only non-secret identifiers.
        log.info("Saved {} credential for userId={} accountId={} (id={})", broker, userId, accountId, saved.getId());
        return toStatus(saved);
    }

    @Transactional
    public void delete(Long userId, Long accountId, Broker broker) {
        repository.findByUserIdAndAccountIdAndBroker(userId, accountId, broker)
                .ifPresent(c -> { repository.delete(c); log.info("Deleted {} credential for userId={} accountId={}", broker, userId, accountId); });
    }

    // ── Decryption for sync services only (returns plaintext; callers must not log it) ──

    /** Decrypted secrets + metadata for a stored credential; used internally by sync services. */
    public record DecryptedCredential(String meta1, String meta2, String secret1, String secret2) {}

    public Optional<DecryptedCredential> decryptFor(Long userId, Long accountId, Broker broker) {
        return repository.findByUserIdAndAccountIdAndBroker(userId, accountId, broker)
                .map(c -> new DecryptedCredential(c.getMeta1(), c.getMeta2(),
                        c.getSecret1() == null ? null : cipher.decrypt(c.getSecret1()),
                        c.getSecret2() == null ? null : cipher.decrypt(c.getSecret2())));
    }
}
