package com.myfinance.saas.auth;

import com.myfinance.saas.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.HexFormat;

/**
 * Creates and validates single-use email-verification / password-reset tokens.
 * Raw tokens are returned to callers (to email) but only their SHA-256 hash is stored.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final OneTimeTokenRepository tokenRepository;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Issue a new raw token for the given purpose and persist only its hash. */
    @Transactional
    public String issue(Long customerId, OneTimeToken.Purpose purpose) {
        String raw = generateRawToken();
        long ttlMs = purpose == OneTimeToken.Purpose.EMAIL_VERIFICATION
                ? appProperties.getJwt().getVerificationExpiration()
                : appProperties.getJwt().getResetExpiration();

        tokenRepository.save(OneTimeToken.builder()
                .customerId(customerId)
                .tokenHash(hash(raw))
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusNanos(ttlMs * 1_000_000))
                .build());
        return raw;
    }

    /**
     * Validate and consume a raw token. Returns the customerId if valid+unused+unexpired,
     * marking it used (single-use). Returns empty otherwise.
     */
    @Transactional
    public Optional<Long> consume(String rawToken, OneTimeToken.Purpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<OneTimeToken> found = tokenRepository.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        OneTimeToken token = found.get();
        if (token.getPurpose() != purpose || !token.isUsable(LocalDateTime.now())) {
            return Optional.empty();
        }
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        return Optional.of(token.getCustomerId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
