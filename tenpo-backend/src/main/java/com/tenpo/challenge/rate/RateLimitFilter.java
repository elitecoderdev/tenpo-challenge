package com.tenpo.challenge.rate;

import com.tenpo.challenge.shared.api.ApiError;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ClientRateLimiter clientRateLimiter;
    private final ClientKeyResolver clientKeyResolver;
    private final ApiErrorFactory apiErrorFactory;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitFilter(
            ClientRateLimiter clientRateLimiter,
            ClientKeyResolver clientKeyResolver,
            ApiErrorFactory apiErrorFactory,
            ObjectMapper objectMapper,
            RateLimitProperties rateLimitProperties
    ) {
        this.clientRateLimiter = clientRateLimiter;
        this.clientKeyResolver = clientKeyResolver;
        this.apiErrorFactory = apiErrorFactory;
        this.objectMapper = objectMapper;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientKey = clientKeyResolver.resolve(request);
        RateLimitDecision decision = clientRateLimiter.tryConsume(clientKey);

        response.setHeader("X-Rate-Limit-Limit", String.valueOf(rateLimitProperties.capacity()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(decision.remainingRequests()));

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfter().toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiError apiError = apiErrorFactory.build(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Only 3 requests per minute are allowed per client.",
                    request.getRequestURI(),
                    List.of()
            );

            objectMapper.writeValue(response.getWriter(), apiError);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
