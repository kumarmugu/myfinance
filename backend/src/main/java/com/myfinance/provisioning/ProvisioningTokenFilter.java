package com.myfinance.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Guards the additive internal provisioning endpoints (/api/internal/provisioning/**).
 *
 * Requires a shared service token in the "X-Provisioning-Token" header, compared in
 * constant time against the configured secret. On success, sets an authenticated
 * principal with ROLE_PROVISIONING so the endpoints are reachable within the existing
 * stateless security chain. This does NOT affect any other route.
 */
@Component
@Slf4j
public class ProvisioningTokenFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/api/internal/provisioning";
    private static final String HEADER = "X-Provisioning-Token";

    private final String configuredToken;
    private final boolean enabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProvisioningTokenFilter(@Value("${app.provisioning.token:}") String configuredToken) {
        this.configuredToken = configuredToken;
        this.enabled = configuredToken != null && !configuredToken.isBlank();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            log.error("Provisioning endpoint called but app.provisioning.token is not configured; rejecting.");
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "Provisioning is not configured");
            return;
        }

        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, configuredToken)) {
            log.warn("Rejected provisioning request with missing/invalid token from {}", request.getRemoteAddr());
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid provisioning token");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "provisioning-service", null, List.of(new SimpleGrantedAuthority("ROLE_PROVISIONING")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
