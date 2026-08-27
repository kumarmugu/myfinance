package com.myfinance.provisioning;

import com.myfinance.model.AppUser;
import com.myfinance.provisioning.dto.ProvisionUserRequest;
import com.myfinance.provisioning.dto.ProvisionUserResponse;
import com.myfinance.provisioning.dto.UpdateAccessRequest;
import com.myfinance.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Server-to-server provisioning of finance-app users on behalf of the SaaS platform.
 *
 * Idempotency: keyed on the (unique) email. Re-provisioning an existing email returns the
 * existing user without creating a duplicate. This is purely additive and does not change
 * the existing self-registration policy (which remains disabled) or admin user management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ProvisionUserResponse provisionUser(ProvisionUserRequest request) {
        String email = normalizeEmail(request.getEmail());

        Optional<AppUser> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            log.info("Provisioning idempotent hit: existing user id={} for email(hash)={}",
                    user.getId(), emailHash(email));
            return toResponse(user, false);
        }

        String username = resolveUniqueUsername(request.getUsername(), email);
        String rawPassword = (request.getInitialPassword() != null && !request.getInitialPassword().isBlank())
                ? request.getInitialPassword()
                : generateRandomPassword();

        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .displayName(request.getDisplayName() != null && !request.getDisplayName().isBlank()
                        ? request.getDisplayName() : username)
                .role("USER")
                .isActive(true)
                .enabledFeatures(request.getEnabledFeatures() != null ? request.getEnabledFeatures() : "")
                .slFdEnabled(request.getEnabledFeatures() != null && request.getEnabledFeatures().contains("SL_FD"))
                .build();

        userRepository.save(user);
        log.info("Provisioned new finance user id={} username={} for email(hash)={}",
                user.getId(), username, emailHash(email));
        return toResponse(user, true);
    }

    @Transactional
    public ProvisionUserResponse updateAccess(UpdateAccessRequest request) {
        String email = normalizeEmail(request.getEmail());
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ProvisioningNotFoundException("No provisioned user for the given email"));

        user.setIsActive(request.getActive());
        if (request.getEnabledFeatures() != null) {
            user.setEnabledFeatures(request.getEnabledFeatures());
            user.setSlFdEnabled(request.getEnabledFeatures().contains("SL_FD"));
        }
        userRepository.save(user);
        log.info("Updated access for user id={} active={} for email(hash)={}",
                user.getId(), request.getActive(), emailHash(email));
        return toResponse(user, false);
    }

    @Transactional(readOnly = true)
    public ProvisionUserResponse getStatusByEmail(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ProvisioningNotFoundException("No provisioned user for the given email"));
        return toResponse(user, false);
    }

    // ── helpers ──

    private ProvisionUserResponse toResponse(AppUser user, boolean created) {
        return ProvisionUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabledFeatures(user.getEnabledFeatures() != null ? user.getEnabledFeatures() : "")
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .created(created)
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String resolveUniqueUsername(String preferred, String email) {
        String base = (preferred != null && !preferred.isBlank())
                ? sanitize(preferred)
                : sanitize(email.substring(0, email.indexOf('@')));
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String sanitize(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "");
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Non-reversible short identifier for logs so raw emails are not written to logs. */
    private String emailHash(String email) {
        return Integer.toHexString(email.hashCode());
    }
}
