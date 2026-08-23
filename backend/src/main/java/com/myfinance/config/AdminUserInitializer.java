package com.myfinance.config;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures an admin user always exists (both dev and prod).
 * Runs before DataInitializer. Only creates admin if no users exist.
 */
@Component
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (appUserRepository.count() == 0) {
            appUserRepository.save(AppUser.builder()
                    .username("admin")
                    .email("admin@myfinance.local")
                    .password(passwordEncoder.encode("admin123"))
                    .displayName("Admin")
                    .role("ADMIN")
                    .build());
            log.info("Admin user created (admin / admin123) — change password immediately!");
        }
    }
}
