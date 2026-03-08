package com.tenpo.challenge.rate;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        int remainingRequests,
        Duration retryAfter
) {

    public static RateLimitDecision allowed(int remainingRequests, Duration retryAfter) {
        return new RateLimitDecision(true, remainingRequests, retryAfter);
    }

    public static RateLimitDecision blocked(Duration retryAfter) {
        return new RateLimitDecision(false, 0, retryAfter);
    }
}
