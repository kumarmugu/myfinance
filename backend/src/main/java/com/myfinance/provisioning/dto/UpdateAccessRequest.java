package com.myfinance.provisioning.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request from the SaaS backend to update a finance-app user's access status based on
 * subscription state (active vs suspended). Maps to AppUser.isActive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccessRequest {

    @NotBlank
    @Email
    private String email;

    /** true = access allowed (active subscription/trial); false = suspend access. */
    @NotNull
    private Boolean active;

    /**
     * Optional updated feature set (e.g. after a plan change). If null, features are left
     * unchanged. Empty string means ALL features.
     */
    private String enabledFeatures;
}
