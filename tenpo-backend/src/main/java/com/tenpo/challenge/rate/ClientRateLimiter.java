package com.tenpo.challenge.rate;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ClientRateLimiter {

    private final LoadingCache<String, FixedWindowCounter> windows;
    private final RateLimitProperties rateLimitProperties;

    public ClientRateLimiter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
        this.windows = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(rateLimitProperties.duration().multipliedBy(2))
                .build(key -> new FixedWindowCounter());
    }

    public RateLimitDecision tryConsume(String clientKey) {
        return windows.get(clientKey)
                .consume(Instant.now(), rateLimitProperties.capacity(), rateLimitProperties.duration());
    }

    private static final class FixedWindowCounter {

        private Instant windowStart;
        private int consumedTokens;

        // English: A synchronized fixed window is enough for this single-node challenge.
        // Espanol: Una ventana fija sincronizada es suficiente para este desafio de una sola instancia.
        synchronized RateLimitDecision consume(Instant now, int capacity, Duration duration) {
            if (Objects.isNull(windowStart) || !now.isBefore(windowStart.plus(duration))) {
                windowStart = now;
                consumedTokens = 0;
            }

            if (consumedTokens < capacity) {
                consumedTokens++;
                return RateLimitDecision.allowed(capacity - consumedTokens, Duration.ZERO);
            }

            Duration retryAfter = Duration.between(now, windowStart.plus(duration));
            long retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1_000);
            return RateLimitDecision.blocked(Duration.ofSeconds(retryAfterSeconds));
        }
    }
}
