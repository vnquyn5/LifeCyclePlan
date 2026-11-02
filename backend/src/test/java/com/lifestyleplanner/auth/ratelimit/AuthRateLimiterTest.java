package com.lifestyleplanner.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.lifestyleplanner.auth.ratelimit.AuthRateLimitProperties.Endpoint;

class AuthRateLimiterTest {

    @Test
    void enforcesIndependentEndpointWindowsAndExpiresWithoutSleeping() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(properties(10, 2, 60, 1, 600), clock);

        assertTrue(limiter.consume(Endpoint.LOGIN, "192.0.2.1").allowed());
        assertTrue(limiter.consume(Endpoint.LOGIN, "192.0.2.1").allowed());
        AuthRateLimiter.Decision loginLimited = limiter.consume(Endpoint.LOGIN, "192.0.2.1");
        assertFalse(loginLimited.allowed());
        assertEquals(60, loginLimited.retryAfterSeconds());

        assertTrue(limiter.consume(Endpoint.REGISTER, "192.0.2.1").allowed());
        AuthRateLimiter.Decision registerLimited = limiter.consume(Endpoint.REGISTER, "192.0.2.1");
        assertFalse(registerLimited.allowed());
        assertEquals(600, registerLimited.retryAfterSeconds());

        clock.advance(Duration.ofSeconds(60));
        assertTrue(limiter.consume(Endpoint.LOGIN, "192.0.2.1").allowed());
        assertFalse(limiter.consume(Endpoint.REGISTER, "192.0.2.1").allowed());
    }

    @Test
    void concurrentRequestsCannotExceedTheConfiguredLimit() throws Exception {
        int maximum = 5;
        AuthRateLimiter limiter = new AuthRateLimiter(properties(100, maximum, 60, 3, 600),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        int requestCount = 80;
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Future<AuthRateLimiter.Decision>> requests = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.consume(Endpoint.LOGIN, "198.51.100.4");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long allowed = 0;
            for (Future<AuthRateLimiter.Decision> request : requests) {
                if (request.get(5, TimeUnit.SECONDS).allowed()) {
                    allowed++;
                }
            }
            assertEquals(maximum, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredEntriesAreCleanedAndTheTrackedSourceLimitIsBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(properties(1, 1, 30, 1, 600), clock);

        assertTrue(limiter.consume(Endpoint.LOGIN, "192.0.2.1").allowed());
        AuthRateLimiter.Decision capacityLimited = limiter.consume(Endpoint.LOGIN, "192.0.2.2");
        assertFalse(capacityLimited.allowed());
        assertEquals(30, capacityLimited.retryAfterSeconds());
        assertEquals(1, limiter.trackedBucketCount());

        clock.advance(Duration.ofSeconds(31));
        assertTrue(limiter.consume(Endpoint.LOGIN, "192.0.2.2").allowed());
        assertEquals(1, limiter.trackedBucketCount());
    }

    private AuthRateLimitProperties properties(
            int maxTrackedBuckets,
            int loginMaxRequests,
            long loginSeconds,
            int registerMaxRequests,
            long registerSeconds) {
        return new AuthRateLimitProperties(
                maxTrackedBuckets,
                loginMaxRequests,
                Duration.ofSeconds(loginSeconds),
                registerMaxRequests,
                Duration.ofSeconds(registerSeconds));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
