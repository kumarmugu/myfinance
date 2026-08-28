package com.myfinance.saas.signup;

import com.myfinance.saas.security.RateLimiter;
import com.myfinance.saas.signup.dto.SignupRequest;
import com.myfinance.saas.signup.dto.SignupResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Public self-service signup endpoint. Rate-limited and protected against basic automated
 * abuse via a honeypot field. A pluggable CAPTCHA verification hook can be added here.
 */
@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Signup", description = "Public self-service signup and 7-day trial")
public class SignupController {

    private final SignupService signupService;
    private final RateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request,
                                    @RequestHeader(value = "X-Honeypot", required = false) String honeypot,
                                    HttpServletRequest http) {
        // Honeypot: legitimate clients leave this empty. Bots that fill hidden fields are
        // silently accepted-looking but not processed (return generic success).
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("Signup rejected by honeypot from {}", clientIp(http));
            return ResponseEntity.ok(SignupResponse.builder()
                    .message("Account created. Please check your email to verify your address.")
                    .provisioned(false).build());
        }

        if (!rateLimiter.tryConsume("signup:" + clientIp(http), 5, Duration.ofMinutes(10))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many signups from this address, please try again later"));
        }

        SignupResponse response = signupService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
