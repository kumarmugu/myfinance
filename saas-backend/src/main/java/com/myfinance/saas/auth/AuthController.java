package com.myfinance.saas.auth;

import com.myfinance.saas.auth.dto.*;
import com.myfinance.saas.security.RateLimiter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Portal customer authentication endpoints. All are public (permitted in SecurityConfig)
 * except /me, which requires a valid portal token. Rate-limited and enumeration-safe.
 *
 * NOTE: This is ISOLATED from the finance app's login. The marketing site's "Login" button
 * redirects users to the finance app; these endpoints are for the billing/subscription portal.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portal Auth", description = "SaaS customer portal authentication (isolated from the finance app)")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryConsume("login:" + clientIp(http), 10, Duration.ofMinutes(1))) {
            return tooManyRequests();
        }
        try {
            LoginResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryConsume("verify:" + clientIp(http), 20, Duration.ofMinutes(1))) {
            return tooManyRequests();
        }
        boolean ok = authService.verifyEmail(request.getToken());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired verification token"));
        }
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryConsume("forgot:" + clientIp(http), 5, Duration.ofMinutes(1))) {
            return tooManyRequests();
        }
        authService.forgotPassword(request.getEmail());
        // Uniform response regardless of account existence (enumeration protection).
        return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryConsume("reset:" + clientIp(http), 10, Duration.ofMinutes(1))) {
            return tooManyRequests();
        }
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password has been reset"));
        } catch (AuthException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomerPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "customerId", principal.customerId(),
                "email", principal.email()));
    }

    private ResponseEntity<?> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many requests, please try again later"));
    }

    /** Best-effort client IP for rate limiting (honours X-Forwarded-For first hop). */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
