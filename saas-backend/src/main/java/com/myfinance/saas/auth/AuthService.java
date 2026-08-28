package com.myfinance.saas.auth;

import com.myfinance.saas.auth.dto.LoginResponse;
import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Portal authentication: login, email verification, and password reset.
 *
 * Security notes:
 * - Login failures are uniform (invalid email vs wrong password are indistinguishable).
 * - forgot-password always reports success (account-enumeration protection).
 * - Passwords are BCrypt-hashed; verification/reset use single-use hashed tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService jwtService;
    private final TokenService tokenService;
    private final PasswordPolicy passwordPolicy;
    private final EmailService emailService;
    private final AppProperties appProperties;

    /** Authenticate a customer. Throws {@link AuthException} with a generic message on failure. */
    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        String normalized = normalize(email);
        Optional<Customer> found = customerRepository.findByEmail(normalized);

        // Always perform a hash comparison to reduce timing side-channels between the
        // "no such user" and "wrong password" cases.
        String storedHash = found.map(Customer::getPasswordHash)
                .orElse("$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidin");
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);

        if (found.isEmpty() || !passwordMatches) {
            log.warn("Portal login failed for email(hash)={}", emailHash(normalized));
            throw new AuthException("Invalid email or password");
        }

        Customer customer = found.get();
        if (customer.getStatus() == CustomerStatus.SUSPENDED) {
            log.warn("Portal login blocked (suspended) for email(hash)={}", emailHash(normalized));
            throw new AuthException("Account is not available");
        }

        String token = jwtService.generateToken(customer.getId(), customer.getEmail());
        log.info("Portal login success for customerId={}", customer.getId());
        return LoginResponse.builder()
                .token(token)
                .customerId(customer.getId())
                .email(customer.getEmail())
                .fullName(customer.getFullName())
                .emailVerified(customer.isEmailVerified())
                .build();
    }

    /** Consume a verification token and mark the customer verified/active. Idempotent-ish. */
    @Transactional
    public boolean verifyEmail(String rawToken) {
        Optional<Long> customerId = tokenService.consume(rawToken, OneTimeToken.Purpose.EMAIL_VERIFICATION);
        if (customerId.isEmpty()) {
            return false;
        }
        Customer customer = customerRepository.findById(customerId.get()).orElse(null);
        if (customer == null) {
            return false;
        }
        customer.setEmailVerified(true);
        if (customer.getStatus() == CustomerStatus.PENDING_VERIFICATION) {
            customer.setStatus(CustomerStatus.ACTIVE);
        }
        customerRepository.save(customer);
        log.info("Email verified for customerId={}", customer.getId());
        return true;
    }

    /**
     * Begin password reset. Always behaves the same regardless of whether the email exists
     * (enumeration protection). Sends an email only when the account exists.
     */
    @Transactional
    public void forgotPassword(String email) {
        String normalized = normalize(email);
        customerRepository.findByEmail(normalized).ifPresent(customer -> {
            String raw = tokenService.issue(customer.getId(), OneTimeToken.Purpose.PASSWORD_RESET);
            String url = appProperties.getPublicWebUrl() + "/reset-password?token=" + raw;
            emailService.sendPasswordResetEmail(customer.getEmail(), url);
        });
        log.info("Password reset requested for email(hash)={}", emailHash(normalized));
    }

    /** Complete password reset with a single-use token. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicy.validate(newPassword).ifPresent(msg -> { throw new AuthException(msg); });

        Long customerId = tokenService.consume(rawToken, OneTimeToken.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> new AuthException("Invalid or expired reset token"));

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AuthException("Invalid or expired reset token"));
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);
        log.info("Password reset completed for customerId={}", customer.getId());
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String emailHash(String email) {
        return email == null ? "null" : Integer.toHexString(email.hashCode());
    }
}
