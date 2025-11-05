package com.tradeengine.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting service using Bucket4j (in-memory)
 * 
 * Rate limits:
 * - Per client: 100 requests per minute
 * - Can be configured per client_id or API key
 */
@Service
public class RateLimitService {
    
    // Per-client rate limit: 100 requests per minute
    private static final int REQUESTS_PER_MINUTE = 100;
    
    private final ConcurrentHashMap<String, Bucket> buckets;
    
    public RateLimitService() {
        this.buckets = new ConcurrentHashMap<>();
    }
    
    /**
     * Check if request is allowed for a client
     * 
     * @param clientId Client identifier
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);
        return bucket.tryConsume(1);
    }
    
    /**
     * Get remaining tokens for a client
     */
    public long getRemainingTokens(String clientId) {
        Bucket bucket = buckets.get(clientId);
        return bucket != null ? bucket.getAvailableTokens() : REQUESTS_PER_MINUTE;
    }
    
    /**
     * Create a rate limit bucket for a client
     */
    private Bucket createBucket(String clientId) {
        Bandwidth limit = Bandwidth.classic(REQUESTS_PER_MINUTE, 
            Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }
    
    /**
     * Reset rate limit for a client (for testing/admin)
     */
    public void resetRateLimit(String clientId) {
        buckets.remove(clientId);
    }
}

