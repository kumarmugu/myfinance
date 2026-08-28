package com.myfinance.saas.auth;

/**
 * Authenticated portal customer, exposed as the Spring Security principal.
 * Controllers resolve the current customer id from this (never from client input) to
 * enforce object-level authorization / tenant isolation.
 */
public record CustomerPrincipal(Long customerId, String email) {
}
