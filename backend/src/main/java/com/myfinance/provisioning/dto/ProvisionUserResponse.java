package com.myfinance.provisioning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned to the SaaS backend after provisioning / status lookup.
 * Never contains passwords or secrets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionUserResponse {

    private Long userId;
    private String username;
    private String email;
    private String enabledFeatures;
    private boolean active;

    /** True if this call created a new user; false if an existing user was returned. */
    private boolean created;
}
