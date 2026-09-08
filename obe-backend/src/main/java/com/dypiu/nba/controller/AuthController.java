package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        System.out.println("Logged In");
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(response)
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Account registered successfully")
                .data(response)
                .build());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Token refreshed successfully")
                .data(response)
                .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String msg = authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message(msg)
                .data(msg)
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String msg = authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message(msg)
                .data(msg)
                .build());
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request.getLoginSessionId(), request.getCode());
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("OTP verified successfully")
                .data(response)
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout() {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Successfully logged out.")
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
