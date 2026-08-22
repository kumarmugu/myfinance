package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Base class for controller tests that need multi-tenant user context.
 * Uses @DirtiesContext to reset the application context (and DB) between test classes.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseControllerTest {

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected AppUser testUser;

    @BeforeEach
    void setupTestUser() {
        if (appUserRepository.findByUsername("user").isEmpty()) {
            testUser = appUserRepository.save(AppUser.builder()
                    .username("user")
                    .email("test@test.com")
                    .password(passwordEncoder.encode("test123"))
                    .displayName("Test User")
                    .role("USER")
                    .build());
        } else {
            testUser = appUserRepository.findByUsername("user").get();
        }
    }
}
