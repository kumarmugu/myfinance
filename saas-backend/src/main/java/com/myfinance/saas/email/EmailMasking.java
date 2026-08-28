package com.myfinance.saas.email;

/**
 * Shared helper for masking email addresses in logs (never log full addresses / PII).
 */
final class EmailMasking {

    private EmailMasking() {}

    static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String masked = local.isEmpty() ? "*" : local.charAt(0) + "***";
        return masked + email.substring(at);
    }
}
