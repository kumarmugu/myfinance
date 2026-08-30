package com.myfinance.saas.auth;

import com.myfinance.saas.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Issues and validates portal access tokens for SaaS customers.
 *
 * These tokens are ISOLATED from the finance app's auth: a distinct signing secret and an
 * "aud=saas-portal" claim, so a portal token can never be used against the finance app and
 * vice versa. Portal tokens grant access ONLY to billing/subscription APIs.
 */
@Service
@RequiredArgsConstructor
public class PortalJwtService {

    private static final String AUDIENCE = "saas-portal";
    private static final String ADMIN_AUDIENCE = "saas-admin";
    private final AppProperties appProperties;

    public String generateToken(Long customerId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .audience().add(AUDIENCE).and()
                .claim("customerId", customerId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + appProperties.getJwt().getExpiration()))
                .signWith(signKey())
                .compact();
    }

    /**
     * Issue an ADMIN token (aud=saas-admin). Distinct audience means an admin token cannot be
     * used on customer portal routes and vice versa, even though both share the signing key.
     */
    public String generateAdminToken(Long adminId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .audience().add(ADMIN_AUDIENCE).and()
                .claim("adminId", adminId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + appProperties.getJwt().getExpiration()))
                .signWith(signKey())
                .compact();
    }

    public Long extractAdminId(String token) {
        return extractClaim(token, c -> c.get("adminId", Long.class));
    }

    /** Validate an admin token: correct audience, not expired, valid signature. */
    public boolean isValidAdmin(String token) {
        try {
            Claims claims = parse(token);
            boolean audienceOk = claims.getAudience() != null && claims.getAudience().contains(ADMIN_AUDIENCE);
            return audienceOk && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractCustomerId(String token) {
        return extractClaim(token, c -> c.get("customerId", Long.class));
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            boolean audienceOk = claims.getAudience() != null && claims.getAudience().contains(AUDIENCE);
            boolean notExpired = claims.getExpiration().after(new Date());
            return audienceOk && notExpired;
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parse(token));
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(signKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signKey() {
        return Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes());
    }

    public Map<String, Object> publicClaims(String token) {
        Claims c = parse(token);
        return Map.of("customerId", c.get("customerId", Long.class), "email", c.getSubject());
    }
}
