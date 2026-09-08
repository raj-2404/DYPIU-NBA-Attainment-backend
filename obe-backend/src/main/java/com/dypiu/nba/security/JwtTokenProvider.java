package com.dypiu.nba.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationInMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long jwtRefreshExpirationInMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getJwtExpirationInMs() {
        return jwtExpirationInMs;
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        return generateTokenForUser(username);
    }

    public String generateTokenForUser(String username) {
        return generateTokenForUser(username, null, null, null, null);
    }

    public String generateTokenForUser(String username, String activeRole, String schoolId, String departmentId, String masterProgrammeId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        var builder = Jwts.builder()
                .subject(username)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate);

        if (activeRole != null && !activeRole.isBlank()) {
            builder.claim("activeRole", activeRole);
        }
        if (schoolId != null && !schoolId.isBlank()) {
            builder.claim("schoolId", schoolId);
        }
        if (departmentId != null && !departmentId.isBlank()) {
            builder.claim("departmentId", departmentId);
        }
        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            builder.claim("masterProgrammeId", masterProgrammeId);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationInMs);

        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims getClaimsFromJwt(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromJwt(String token) {
        Claims claims = getClaimsFromJwt(token);
        return claims.getSubject();
    }

    public String getActiveRoleFromJwt(String token) {
        try {
            Claims claims = getClaimsFromJwt(token);
            return claims.get("activeRole", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getSchoolIdFromJwt(String token) {
        try {
            Claims claims = getClaimsFromJwt(token);
            return claims.get("schoolId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getDepartmentIdFromJwt(String token) {
        try {
            Claims claims = getClaimsFromJwt(token);
            return claims.get("departmentId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String getMasterProgrammeIdFromJwt(String token) {
        try {
            Claims claims = getClaimsFromJwt(token);
            return claims.get("masterProgrammeId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean validateRefreshToken(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
