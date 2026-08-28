package com.myfinance.saas.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates portal requests using the customer JWT (isolated from the finance app).
 * On a valid token, sets a principal of {@link CustomerPrincipal} with ROLE_CUSTOMER.
 * Invalid/absent tokens simply proceed unauthenticated (protected routes then 401/403).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortalJwtFilter extends OncePerRequestFilter {

    private final PortalJwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long customerId = jwtService.extractCustomerId(token);
                    String email = jwtService.extractEmail(token);
                    var principal = new CustomerPrincipal(customerId, email);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.debug("Portal JWT authentication skipped: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
