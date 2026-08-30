package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserManagementController {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<?> getAll() {
        if (!tenantContext.isAdmin()) {
            log.warn("Non-admin user attempted to list users");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        List<AppUser> users = userRepository.findAll();
        // Don't expose passwords
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> request) {
        if (!tenantContext.isAdmin()) {
            log.warn("Non-admin user attempted to create user");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }

        String username = request.get("username");
        String email = request.get("email");
        String password = request.get("password");
        String displayName = request.getOrDefault("displayName", username);
        String role = request.getOrDefault("role", "USER");

        if (userRepository.existsByUsername(username)) {
            log.warn("User creation failed: username={} already taken", username);
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }

        log.info("Creating user: username={}, role={}", username, role);
        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .displayName(displayName)
                .role(role)
                .enabledFeatures(request.getOrDefault("enabledFeatures", ""))
                .baseCurrency(normCurrency(request.get("baseCurrency")))
                .displayCurrencies(normCsv(request.get("displayCurrencies")))
                .build();

        userRepository.save(user);
        log.info("Created user id={}, username={}", user.getId(), username);
        user.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (!tenantContext.isAdmin()) {
            log.warn("Non-admin user attempted to update user role");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        log.info("Updating role for user id={} to {}", id, request.get("role"));
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(request.get("role"));
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        if (!tenantContext.isAdmin()) {
            log.warn("Non-admin user attempted to toggle user active status");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(!user.getIsActive());
        log.info("Toggled user id={} active={}", id, user.getIsActive());
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/toggle-sl-fd")
    public ResponseEntity<?> toggleSlFd(@PathVariable Long id) {
        if (!tenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setSlFdEnabled(!Boolean.TRUE.equals(user.getSlFdEnabled()));
        log.info("Toggled user id={} slFdEnabled={}", id, user.getSlFdEnabled());
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/features")
    public ResponseEntity<?> updateFeatures(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (!tenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        String features = request.getOrDefault("enabledFeatures", "");
        user.setEnabledFeatures(features);
        // Keep slFdEnabled in sync
        user.setSlFdEnabled(features.contains("SL_FD"));
        log.info("Updated features for user id={}: {}", id, features);
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    /**
     * Set a user's base/reporting currency and the display-currency options shown in the UI toggle.
     * Both are reporting/display concepts; changing them never mutates any stored original values.
     */
    @PutMapping("/{id}/currency-settings")
    public ResponseEntity<?> updateCurrencySettings(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (!tenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (request.containsKey("baseCurrency")) {
            user.setBaseCurrency(normCurrency(request.get("baseCurrency")));
        }
        if (request.containsKey("displayCurrencies")) {
            user.setDisplayCurrencies(normCsv(request.get("displayCurrencies")));
        }
        log.info("Updated currency settings for user id={}: base={}, display={}",
                id, user.getBaseCurrency(), user.getDisplayCurrencies());
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    private String normCurrency(String c) {
        if (c == null || c.isBlank()) return null;
        return c.trim().toUpperCase();
    }

    private String normCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return java.util.Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
