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
     * @throws RuntimeException if no user is authenticated
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("No authenticated user");
        }
        String username = auth.getName();
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getId();
    }

    /**
     * Get the current authenticated user entity.
     */
    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("No authenticated user");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Check if current user has ADMIN role.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(getCurrentUser().getRole());
    }
}
