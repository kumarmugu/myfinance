package com.myfinance.saas.signup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Signup result. Intentionally minimal and enumeration-safe: the same shape is returned
 * whether the account is new or already exists (see SignupService).
 */
@Data
@Builder
@AllArgsConstructor
public class SignupResponse {
    private String message;
    /** True if provisioning into the finance app succeeded synchronously. */
    private boolean provisioned;
}
