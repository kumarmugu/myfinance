package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserManagementController {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;

    @GetMapping
    public ResponseEntity<?> getAll() {
        if (!tenantContext.isAdmin()) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }

        String username = request.get("username");
        String email = request.get("email");
        String password = request.get("password");
        String displayName = request.getOrDefault("displayName", username);
        String role = request.getOrDefault("role", "USER");

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }

        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .displayName(displayName)
                .role(role)
                .build();

        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        if (!tenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(request.get("role"));
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        if (!tenantContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin access required"));
        }
        AppUser user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }
}
