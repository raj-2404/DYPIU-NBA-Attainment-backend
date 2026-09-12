package com.dypiu.nba.controller;
import lombok.extern.slf4j.Slf4j;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.dypiu.nba.security.AuthRateLimiterService rateLimiterService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "login", 10);
        AuthResponse response = authService.login(request);
        rateLimiterService.resetRateLimit(servletRequest, "login");
        log.debug("Logged In");
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(response)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "register", 5);
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Account registered successfully")
                .data(response)
                .build());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "refresh-token", 20);
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Token refreshed successfully")
                .data(response)
                .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "forgot-password", 5);
        String msg = authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message(msg)
                .data(msg)
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "reset-password", 5);
        String msg = authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message(msg)
                .data(msg)
                .build());
    }

    @PostMapping("/generate-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateOtp(@RequestParam("identifier") String identifier, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "generate-otp", 5);
        Map<String, String> result = authService.generateOtpSession(identifier);
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message(result.get("message"))
                .data(result)
                .build());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        rateLimiterService.checkRateLimit(servletRequest, "verify-otp", 5);
        AuthResponse response = authService.verifyOtp(request.getLoginSessionId(), request.getCode());
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("OTP verified successfully")
                .data(response)
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody(required = false) RefreshTokenRequest refreshRequest) {
        String bearerToken = request.getHeader("Authorization");
        String accessToken = (bearerToken != null && bearerToken.startsWith("Bearer ")) ? bearerToken.substring(7) : null;
        String refreshToken = refreshRequest != null ? refreshRequest.getRefreshToken() : request.getHeader("X-Refresh-Token");

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Successfully logged out and session revoked.")
                .data(Map.of("loggedOut", true))
                .build());
    }

    @GetMapping({"/roles", "/profiles"})
    public ResponseEntity<ApiResponse<UserRolesResponseDto>> getAvailableRoles(java.security.Principal principal) {
        UserRolesResponseDto response = authService.getAvailableRoles(principal);
        return ResponseEntity.ok(ApiResponse.<UserRolesResponseDto>builder()
                .success(true)
                .message("User roles and profiles retrieved successfully")
                .data(response)
                .build());
    }

    @PostMapping("/switch-role")
    public ResponseEntity<ApiResponse<AuthResponse>> switchRole(@Valid @RequestBody SwitchRoleRequestDto request, java.security.Principal principal) {
        AuthResponse response = authService.switchRole(request, principal);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Active profile switched to " + request.getRole() + " successfully")
                .data(response)
                .build());
    }
}
