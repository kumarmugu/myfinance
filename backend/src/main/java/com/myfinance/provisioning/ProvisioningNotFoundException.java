package com.myfinance.provisioning;

/**
 * Raised when a provisioning lookup/update targets an email that has no finance-app user.
 */
public class ProvisioningNotFoundException extends RuntimeException {
    public ProvisioningNotFoundException(String message) {
        super(message);
    }
}
