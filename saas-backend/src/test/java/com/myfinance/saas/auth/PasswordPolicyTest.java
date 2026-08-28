package com.myfinance.saas.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void rejectsTooShort() {
        assertFalse(policy.isValid("ab1"));
        assertTrue(policy.validate("ab1").isPresent());
    }

    @Test
    void rejectsMissingDigit() {
        assertFalse(policy.isValid("abcdefgh"));
    }

    @Test
    void rejectsMissingLetter() {
        assertFalse(policy.isValid("12345678"));
    }

    @Test
    void rejectsNull() {
        assertFalse(policy.isValid(null));
    }

    @Test
    void acceptsValidPassword() {
        assertTrue(policy.isValid("password1"));
        assertTrue(policy.validate("password1").isEmpty());
    }
}
