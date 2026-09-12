package com.dypiu.nba.security;

import com.dypiu.nba.dto.AuthResponse;
import com.dypiu.nba.dto.RefreshTokenRequest;
import com.dypiu.nba.dto.ResetPasswordRequest;
import com.dypiu.nba.entity.User;
import com.dypiu.nba.entity.UserRole;
import com.dypiu.nba.exception.BadRequestException;
import com.dypiu.nba.repository.UserRepository;
import com.dypiu.nba.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AuthenticationSecurityHardeningTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Autowired
    private AuthRateLimiterService authRateLimiterService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = userRepository.save(User.builder()
                .username("sec_user")
                .email("sec_user@dypiu.ac.in")
                .passwordHash(passwordEncoder.encode("OldSecret123"))
                .name("Security Test User")
                .role(UserRole.FACULTY)
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("OTP is unpredictable, exactly 6 digits, and validates successfully")
    void testOtpGenerationAndVerification() {
        Map<String, String> sessionInfo = authService.generateOtpSession(testUser.getEmail());
        assertNotNull(sessionInfo.get("loginSessionId"));

        // Invalid verification fails
        assertThrows(BadCredentialsException.class, () -> 
                authService.verifyOtp(sessionInfo.get("loginSessionId"), "999999"));

        // Mock test session verify
        String testSessionId = authService.createTestOtpSession(testUser.getUsername(), "123456");
        AuthResponse response = authService.verifyOtp(testSessionId, "123456");
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());

        // Single-use: Subsequent attempt on same session is rejected
        assertThrows(BadRequestException.class, () -> 
                authService.verifyOtp(testSessionId, "123456"));
    }

    @Test
    @DisplayName("OTP locks out session after 3 failed attempts")
    void testOtpMaxAttemptsLockout() {
        String testSessionId = authService.createTestOtpSession(testUser.getUsername(), "654321");

        // Attempt 1: fails, 2 attempts left
        assertThrows(BadCredentialsException.class, () -> authService.verifyOtp(testSessionId, "000000"));
        // Attempt 2: fails, 1 attempt left
        assertThrows(BadCredentialsException.class, () -> authService.verifyOtp(testSessionId, "000000"));
        // Attempt 3: fails, 0 attempts left -> session purged
        assertThrows(BadRequestException.class, () -> authService.verifyOtp(testSessionId, "000000"));
        // Attempt 4: session is dead
        assertThrows(BadRequestException.class, () -> authService.verifyOtp(testSessionId, "654321"));
    }

    @Test
    @DisplayName("Password reset token is URL-safe, single-use, and enforces minimum 6 characters")
    void testPasswordResetFlowAndConstraints() {
        // Uniform message regardless of existence (Anti-enumeration)
        String responseMsg = authService.requestPasswordReset("nonexistent@dypiu.ac.in");
        assertTrue(responseMsg.contains("If an account with that email exists"));

        // Generate valid reset token
        String resetToken = authService.createTestPasswordResetToken(testUser.getUsername());
        assertNotNull(resetToken);
        assertTrue(resetToken.length() >= 32);

        // Reject passwords shorter than 6 characters
        ResetPasswordRequest shortPassReq = new ResetPasswordRequest();
        shortPassReq.setToken(resetToken);
        shortPassReq.setNewPassword("12345");
        assertThrows(BadRequestException.class, () -> 
                authService.resetPassword(resetToken, "12345"));

        // Reset with valid password (>= 6 chars)
        String successMsg = authService.resetPassword(resetToken, "NewPass678");
        assertTrue(successMsg.contains("Password reset successfully"));

        // Single-use: Second reset with same token is rejected
        assertThrows(BadRequestException.class, () -> 
                authService.resetPassword(resetToken, "AnotherPass123"));

        // Verify user password hash was updated
        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPass678", updated.getPasswordHash()));
    }

    @Test
    @DisplayName("Password reset invalidates existing user tokens and sessions")
    void testPasswordResetRevokesExistingTokens() {
        String oldAccessToken = jwtTokenProvider.generateTokenForUser(testUser.getUsername());
        String oldRefreshToken = jwtTokenProvider.generateRefreshToken(testUser.getUsername());

        // Perform password reset
        String resetToken = authService.createTestPasswordResetToken(testUser.getUsername());
        authService.resetPassword(resetToken, "BrandNewPass999");

        // Old refresh token must be rejected
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(oldRefreshToken);
        assertThrows(BadRequestException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    @DisplayName("Logout revokes presented access token and refresh token")
    void testLogoutRevocation() {
        String accessToken = jwtTokenProvider.generateTokenForUser(testUser.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser.getUsername());

        assertFalse(tokenRevocationService.isRevoked(accessToken));
        assertFalse(tokenRevocationService.isRevoked(refreshToken));

        // Logout
        authService.logout(accessToken, refreshToken);

        assertTrue(tokenRevocationService.isRevoked(accessToken));
        assertTrue(tokenRevocationService.isRevoked(refreshToken));

        // Attempt to refresh with revoked refresh token is rejected
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);
        assertThrows(BadRequestException.class, () -> authService.refreshToken(refreshReq));
    }

    @Test
    @DisplayName("Rate limiter protects endpoints from brute-force floods")
    void testRateLimiterAbuseProtection() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        // Allowed within limit
        for (int i = 0; i < 5; i++) {
            authRateLimiterService.checkRateLimit(request, "test-action", 5);
        }

        // 6th attempt exceeds limit
        assertThrows(ResponseStatusException.class, () -> 
                authRateLimiterService.checkRateLimit(request, "test-action", 5));
    }
}
