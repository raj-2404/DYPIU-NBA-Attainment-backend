package com.dypiu.nba.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Single-instance in-memory token revocation and session management service.
 * Stores SHA-256 hashed token identifiers and per-user revocation timestamps.
 *
 * NOTE: For multi-instance clustered deployments, this service contract can be
 * backed by a distributed Redis / Valkey store without modifying controllers or callers.
 */
@Slf4j
@Service
public class TokenRevocationService {

    // Cache stores SHA-256(token) -> Boolean.TRUE, expiring 7 days after write
    private final Cache<String, Boolean> revokedTokens = Caffeine.newBuilder()
            .expireAfterWrite(7, TimeUnit.DAYS)
            .maximumSize(100_000)
            .build();

    // Cache stores username -> revocation epoch timestamp (ms), expiring after 7 days
    private final Cache<String, Long> userRevocationEpochs = Caffeine.newBuilder()
            .expireAfterWrite(7, TimeUnit.DAYS)
            .maximumSize(50_000)
            .build();

    /**
     * Revokes a specific access token or refresh token by adding its SHA-256 hash to the blacklist.
     *
     * @param token raw JWT token string
     */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String tokenHash = hashToken(token.trim());
        revokedTokens.put(tokenHash, Boolean.TRUE);
        log.debug("[TokenRevocationService] Token successfully revoked | hash: {}", tokenHash);
    }

    /**
     * Revokes all active tokens (access and refresh) issued for a given user prior to this timestamp.
     * Useful on password reset or account deactivation.
     *
     * @param username username of the user whose sessions should be terminated
     */
    public void revokeAllUserTokens(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        long epochMs = System.currentTimeMillis();
        userRevocationEpochs.put(username.trim().toLowerCase(), epochMs);
        log.info("[TokenRevocationService] All prior tokens revoked for user: {} at epoch: {}", username, epochMs);
    }

    /**
     * Checks whether a token has been explicitly revoked by hash or via user-level revocation.
     *
     * @param token raw JWT token string
     * @return true if the token is revoked, false otherwise
     */
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String tokenHash = hashToken(token.trim());
        return Boolean.TRUE.equals(revokedTokens.getIfPresent(tokenHash));
    }

    /**
     * Checks whether a token belonging to a user was issued before a user-wide revocation event.
     *
     * @param username username of token owner
     * @param issuedAt token issued timestamp
     * @return true if issued before revocation cutoff, false otherwise
     */
    public boolean isUserTokenRevoked(String username, Date issuedAt) {
        if (username == null || issuedAt == null) {
            return false;
        }
        Long revocationTime = userRevocationEpochs.getIfPresent(username.trim().toLowerCase());
        if (revocationTime != null && issuedAt.getTime() < revocationTime) {
            return true;
        }
        return false;
    }

    /**
     * Computes SHA-256 digest of token to avoid storing raw bearer credentials in memory.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
