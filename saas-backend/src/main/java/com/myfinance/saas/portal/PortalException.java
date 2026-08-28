package com.myfinance.saas.portal;

/**
 * Raised for portal business errors (e.g. missing subscription, unavailable plan).
 */
public class PortalException extends RuntimeException {
    public PortalException(String message) {
        super(message);
    }
}
