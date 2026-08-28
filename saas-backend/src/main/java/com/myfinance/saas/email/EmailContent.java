package com.myfinance.saas.email;

/**
 * A rendered email: subject plus HTML and plain-text bodies.
 */
public record EmailContent(String subject, String html, String text) {
}
