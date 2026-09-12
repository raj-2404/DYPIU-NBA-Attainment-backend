package com.dypiu.nba.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter for authentication and password-reset endpoints.
 * Protects against credential stuffing, OTP brute-force, and resource exhaustion.
 *
 * Single-node in-memory architecture using Caffeine cache.
 */
@Slf4j
@Service
public class AuthRateLimiterService {

    // 1-minute sliding window rate limiting cache: key -> attempt count
    private final Cache<String, AtomicInteger> rateLimitCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    /**
     * Asserts that the request from the client does not exceed the allowed threshold.
     *
     * @param request HttpServletRequest to extract client IP
     * @param action unique action tag (e.g., "login", "verify-otp", "forgot-password", "reset-password")
     * @param maxAttempts maximum attempts permitted within 1 minute
     */
    public void checkRateLimit(HttpServletRequest request, String action, int maxAttempts) {
        String clientIp = resolveClientIp(request);
        String key = action + ":" + clientIp;

        AtomicInteger counter = rateLimitCache.get(key, k -> new AtomicInteger(0));
        int currentAttempts = counter.incrementAndGet();

        if (currentAttempts > maxAttempts) {
            log.warn("[AuthRateLimiter] Rate limit exceeded for action: {} from IP: {} (attempts: {})", action, clientIp, currentAttempts);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again after 1 minute.");
        }
    }

    /**
     * Resets rate limit counter upon successful action (e.g. valid login).
     */
    public void resetRateLimit(HttpServletRequest request, String action) {
        String clientIp = resolveClientIp(request);
        String key = action + ":" + clientIp;
        rateLimitCache.invalidate(key);
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
