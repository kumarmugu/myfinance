package com.myfinance.saas.auth;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Server-side password policy for portal accounts. Frontend validation is convenience only;
 * this is the authoritative check.
 */
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    /**
     * Returns an error message if the password is unacceptable, or empty if valid.
     */
    public Optional<String> validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("Password must be at most " + MAX_LENGTH + " characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return Optional.of("Password must contain both letters and numbers");
        }
        return Optional.empty();
    }

    public boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
