package com.myfinance.provisioning.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request from the SaaS backend to provision (idempotently create) a finance-app user.
 * Additive integration surface — does not alter existing auth/registration behavior.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionUserRequest {

    /** External reference from the SaaS platform (e.g. customer id). Logged, not stored. */
    private String externalId;

    @NotBlank
    @Email
    private String email;

    /** Optional preferred username; if blank, derived from the email local-part. */
    private String username;

    private String displayName;

    /**
     * Optional pre-hashed password is NOT accepted. A random password is generated and
     * the user must use the finance app's forgot-password / reset flow, OR the SaaS layer
     * can pass an initial password to set. If blank, a random one is generated.
     */
    private String initialPassword;

    /**
     * Comma-separated feature keys mapped from the subscription plan.
     * Empty string means ALL features (existing app convention).
     */
    private String enabledFeatures;
}
