package com.myfinance.saas.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token-bucket rate limiter keyed by an arbitrary string (e.g.
 * "login:1.2.3.4"). Suitable for a single-node deployment; a distributed store (Redis)
 * would be used for multi-node. Protects auth/signup/reset endpoints from brute force
 * and abuse.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Attempt to consume one token for the key, allowing {@code capacity} requests per
     * {@code window}. Returns true if allowed, false if the limit is exceeded.
     */
    public boolean tryConsume(String key, int capacity, Duration window) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, window));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, Duration window) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, window));
        return Bucket.builder().addLimit(limit).build();
    }

    /** For tests: clear all buckets. */
    public void reset() {
        buckets.clear();
    }
}
