package com.myfinance.security;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Provides the current authenticated user's ID for multi-tenant data isolation.
 * All data queries should filter by this userId to prevent cross-tenant access.
 */
@Component
@RequiredArgsConstructor
public class TenantContext {

    private final AppUserRepository userRepository;

    /**
     * Get the current authenticated user's ID.
     * Returns null if no user is authenticated (e.g., during startup/initialization).
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        AppUser user = userRepository.findByUsername(username)
                .orElse(null);
        return user != null ? user.getId() : null;
    }

    /**
     * Get the current authenticated user entity.
     */
    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    /**
     * Check if current user has ADMIN role.
     */
    public boolean isAdmin() {
        AppUser user = getCurrentUser();
        return user != null && "ADMIN".equals(user.getRole());
    }
}
