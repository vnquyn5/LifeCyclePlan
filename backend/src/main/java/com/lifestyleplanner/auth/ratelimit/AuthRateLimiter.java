package com.lifestyleplanner.auth.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.lifestyleplanner.auth.ratelimit.AuthRateLimitProperties.Endpoint;
import com.lifestyleplanner.auth.ratelimit.AuthRateLimitProperties.Policy;

/**
 * A bounded, single-process fixed-window limiter for the public auth endpoints.
 * Keys contain only the endpoint and the socket peer address; request bodies and
 * account identifiers are never inspected or retained.
 */
public class AuthRateLimiter {
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(30);

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final Map<BucketKey, Bucket> buckets = new HashMap<>();
    private Instant cleanupDue = Instant.MIN;

    public AuthRateLimiter(AuthRateLimitProperties properties, Clock authRateLimitClock) {
        this.properties = properties;
        this.clock = authRateLimitClock;
    }

    public synchronized Decision consume(Endpoint endpoint, String remoteAddress) {
        Instant now = clock.instant();
        cleanupIfDue(now);

        String source = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        BucketKey key = new BucketKey(endpoint, source);
        Policy policy = properties.policyFor(endpoint);
        Bucket bucket = buckets.get(key);

        if (bucket != null && now.isBefore(bucket.expiresAt)) {
            if (bucket.requests >= policy.maxRequests()) {
                return Decision.limitedDecision(retryAfterSeconds(now, bucket.expiresAt));
            }
            bucket.requests++;
            return Decision.allowedDecision();
        }
        if (bucket != null) {
            buckets.remove(key);
        }

        if (buckets.size() >= properties.maxTrackedBuckets()) {
            Instant earliestExpiry = buckets.values().stream()
                    .map(value -> value.expiresAt)
                    .min(Instant::compareTo)
                    .orElse(now.plus(CLEANUP_INTERVAL));
            Instant retryAt = earliestExpiry.isAfter(now) ? earliestExpiry : cleanupDue;
            return Decision.limitedDecision(retryAfterSeconds(now, retryAt));
        }

        buckets.put(key, new Bucket(1, now.plus(policy.window())));
        return Decision.allowedDecision();
    }

    synchronized int trackedBucketCount() {
        return buckets.size();
    }

    private void cleanupIfDue(Instant now) {
        if (!now.isBefore(cleanupDue)) {
            cleanupExpired(now);
            cleanupDue = now.plus(CLEANUP_INTERVAL);
        }
    }

    private void cleanupExpired(Instant now) {
        Iterator<Bucket> iterator = buckets.values().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().expiresAt)) {
                iterator.remove();
            }
        }
    }

    private long retryAfterSeconds(Instant now, Instant expiry) {
        Duration remaining = Duration.between(now, expiry);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }

    private record BucketKey(Endpoint endpoint, String remoteAddress) {
    }

    private static final class Bucket {
        private int requests;
        private final Instant expiresAt;

        private Bucket(int requests, Instant expiresAt) {
            this.requests = requests;
            this.expiresAt = expiresAt;
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        private static Decision allowedDecision() {
            return new Decision(true, 0);
        }

        private static Decision limitedDecision(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
