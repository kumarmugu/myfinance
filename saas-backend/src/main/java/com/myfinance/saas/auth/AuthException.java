package com.myfinance.saas.auth;

/**
 * Raised on authentication/authorization failures with a client-safe message.
 * Messages must never reveal whether an account exists (enumeration protection).
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
