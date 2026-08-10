package vn.edu.epu.eoffice_be.auth.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.epu.eoffice_be.jwt.config.JwtProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LoginRateLimiter {

    private static final class Bucket {
        final Duration window;
        final int limit;
        Instant windowStart;
        AtomicInteger count;

        Bucket(Duration window, int limit) {
            this.window = window;
            this.limit = limit;
            this.windowStart = Instant.now();
            this.count = new AtomicInteger(0);
        }

        synchronized boolean allow() {
            Instant now = Instant.now();
            if (now.isBefore(windowStart.plus(window))) {
                return count.incrementAndGet() <= limit;
            }
            windowStart = now;
            count.set(1);
            return true;
        }

        synchronized Instant lockoutUntil() {
            return windowStart.plus(window);
        }
    }

    private final JwtProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientKey) {
        return buckets.computeIfAbsent(clientKey, k -> new Bucket(getWindow(), getLimit())).allow();
    }

    public Instant lockoutUntil(String clientKey) {
        Bucket bucket = buckets.get(clientKey);
        return bucket == null ? null : bucket.lockoutUntil();
    }

    private Duration getWindow() {
        return Duration.ofSeconds(properties.getAuthRateWindowSeconds());
    }

    private int getLimit() {
        return properties.getAuthRateLimit();
    }
}