package com.dypiu.nba.service;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.BadCredentialsException;
import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.User;
import com.dypiu.nba.entity.UserRole;
import com.dypiu.nba.exception.BadRequestException;
import com.dypiu.nba.repository.UserRepository;
import com.dypiu.nba.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.dypiu.nba.security.TokenRevocationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenRevocationService tokenRevocationService;

        private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    // Single-use password reset tokens: SHA-256(token) -> username, expiring in 15 minutes
    private final Cache<String, String> passwordResetTokenCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    // In-memory OTP session data structure
    @lombok.Value
    public static class OtpSession {
        String username;
        String otpCode;
        AtomicInteger attemptsRemaining;
    }

    // OTP sessions: loginSessionId (UUID) -> OtpSession, expiring in 5 minutes
    private final Cache<String, OtpSession> otpSessionCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.debug("[AuthService] login called");
        String rawIdentifier = request.getUsername() != null ? request.getUsername() : request.getEmail();
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new BadCredentialsException("Username or email is required");
        }

        String rawPassword = request.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadCredentialsException("Password is required");
        }

        // Clean & trim email or username input
        String identifier = rawIdentifier.trim();

        // 1. Check if user exists by email or username
        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new BadCredentialsException("Invalid email/username or password")));

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new BadRequestException("User account is deactivated");
        }

        // 2. Verify hashed password against database passwordHash
        if (!passwordEncoder.matches(rawPassword.trim(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email/username or password");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("[AuthService] register called");
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        UserRole userRole = UserRole.FACULTY;
        if (request.getRole() != null) {
            try {
                userRole = UserRole.valueOf(request.getRole().toUpperCase());
            } catch (Exception ignored) {}
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName() != null ? request.getName() : request.getUsername())
                .role(userRole)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        return buildAuthResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.debug("[AuthService] refreshToken called");
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || !tokenProvider.validateRefreshToken(refreshToken) || tokenRevocationService.isRevoked(refreshToken)) {
            throw new BadRequestException("Invalid, expired, or revoked refresh token");
        }

        String username = tokenProvider.getUsernameFromJwt(refreshToken);
        java.util.Date issuedAt = tokenProvider.getIssuedAtFromJwt(refreshToken);
        if (tokenRevocationService.isUserTokenRevoked(username, issuedAt)) {
            throw new BadRequestException("Session credentials were invalidated. Please log in again.");
        }

        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new BadRequestException("User not found for refresh token"));

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new BadRequestException("User account is deactivated");
        }

        // Rotate: revoke the previously used refresh token to prevent replay
        tokenRevocationService.revokeToken(refreshToken);

        return buildAuthResponse(user);
    }

    public String requestPasswordReset(String email) {
        log.debug("[AuthService] requestPasswordReset called");
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required for password reset");
        }
        String cleanEmail = email.trim();
        Optional<User> userOpt = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(cleanEmail, cleanEmail);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsernameOrEmail(cleanEmail, cleanEmail);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getIsActive() != null && user.getIsActive()) {
                // Generate cryptographically secure 256-bit URL-safe reset token
                byte[] randomBytes = new byte[32];
                secureRandom.nextBytes(randomBytes);
                String rawResetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

                // Store only SHA-256 hash in cache
                String tokenHash = tokenRevocationService.hashToken(rawResetToken);
                passwordResetTokenCache.put(tokenHash, user.getUsername());
                log.info("[AuthService] Generated password reset token for user (valid for 15 minutes)");
            }
        }

        // Generic response to prevent user enumeration attacks
        return "If an account with that email exists, a password reset token has been dispatched.";
    }

    @Transactional
    public String resetPassword(String token, String newPassword) {
        log.debug("[AuthService] resetPassword called");
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Password reset token is required");
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters in length");
        }
        String cleanToken = token.trim();
        String tokenHash = tokenRevocationService.hashToken(cleanToken);
        String username = passwordResetTokenCache.getIfPresent(tokenHash);
        if (username == null) {
            throw new BadRequestException("Invalid or expired password reset token. Please request a new one.");
        }

        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new BadRequestException("User associated with token not found")));

        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);

        // Invalidate single-use reset token and terminate all existing user sessions/tokens
        passwordResetTokenCache.invalidate(tokenHash);
        tokenRevocationService.revokeAllUserTokens(username);
        log.info("[AuthService] Successfully reset password and revoked prior sessions for user: {}", username);
        return "Password reset successfully. Please login with your new credentials.";
    }

    public Map<String, String> generateOtpSession(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Username or email is required to generate OTP");
        }
        String cleanId = identifier.trim();
        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(cleanId, cleanId)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(cleanId, cleanId)
                .orElseThrow(() -> new BadRequestException("User not found: " + cleanId)));

        String loginSessionId = UUID.randomUUID().toString();
        // Generate unpredictable 6-digit numeric OTP using SecureRandom
        int randomCode = secureRandom.nextInt(1_000_000);
        String otpCode = String.format("%06d", randomCode);

        otpSessionCache.put(loginSessionId, new OtpSession(user.getUsername(), otpCode, new AtomicInteger(3)));
        log.info("[AuthService] Generated OTP session for user (valid for 5 minutes)");
        return Map.of("loginSessionId", loginSessionId, "message", "OTP generated and dispatched successfully");
    }

    @Transactional(readOnly = true)
    public AuthResponse verifyOtp(String loginSessionId, String code) {
        log.debug("[AuthService] verifyOtp called");
        if (loginSessionId == null || loginSessionId.isBlank()) {
            throw new BadRequestException("Login session ID is required for OTP verification");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("OTP code is required");
        }
        String cleanSession = loginSessionId.trim();
        OtpSession session = otpSessionCache.getIfPresent(cleanSession);
        if (session == null) {
            throw new BadRequestException("Invalid or expired verification session. Please request a new OTP.");
        }

        int remainingAttempts = session.getAttemptsRemaining().decrementAndGet();
        if (!session.getOtpCode().equals(code.trim())) {
            if (remainingAttempts <= 0) {
                otpSessionCache.invalidate(cleanSession);
                throw new BadRequestException("Verification session terminated due to excessive invalid attempts.");
            }
            throw new BadCredentialsException("Invalid verification code. Remaining attempts: " + remainingAttempts);
        }

        otpSessionCache.invalidate(cleanSession);
        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(session.getUsername(), session.getUsername())
                .orElseGet(() -> userRepository.findByUsernameOrEmail(session.getUsername(), session.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found for session: " + session.getUsername())));

        log.info("[AuthService] OTP verification successful for user: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            tokenRevocationService.revokeToken(accessToken.trim());
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenRevocationService.revokeToken(refreshToken.trim());
        }
        log.info("[AuthService] User session successfully terminated and tokens revoked upon logout");
    }

    // Helper for testing internal token creation
    public String createTestPasswordResetToken(String username) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawResetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = tokenRevocationService.hashToken(rawResetToken);
        passwordResetTokenCache.put(tokenHash, username);
        return rawResetToken;
    }

    public String createTestOtpSession(String username, String code) {
        String loginSessionId = UUID.randomUUID().toString();
        otpSessionCache.put(loginSessionId, new OtpSession(username, code, new AtomicInteger(3)));
        return loginSessionId;
    }

    private final com.dypiu.nba.repository.SchoolRepository schoolRepository;
    private final com.dypiu.nba.repository.DepartmentRepository departmentRepository;
    private final com.dypiu.nba.repository.MasterProgrammeRepository masterProgrammeRepository;
    private final com.dypiu.nba.repository.ProgrammeBatchRepository programmeBatchRepository;
    private final com.dypiu.nba.repository.ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Transactional(readOnly = true)
    public UserRolesResponseDto getAvailableRoles(java.security.Principal principal) {
        String identifier = principal != null ? principal.getName() : null;
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Principal cannot be empty");
        }

        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new BadRequestException("User not found: " + identifier)));

        String activeRole = resolveActiveRole(user);

        List<String> rawRoles = user.getExplicitAssignedRoles();
        Set<String> explicitRoles = rawRoles.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        boolean hasExplicitRoles = !explicitRoles.isEmpty();

        boolean isIqacEligible = hasExplicitRoles 
                ? explicitRoles.contains("IQAC")
                : (user.getRole() == UserRole.IQAC);

        // Check contextual DB matches if not explicitly restricted
        List<com.dypiu.nba.entity.School> allSchools = schoolRepository.findAll();
        com.dypiu.nba.entity.School matchedSchool = allSchools.stream()
                .filter(s -> (user.getSchoolId() != null && user.getSchoolId().equalsIgnoreCase(s.getId()))
                        || (s.getDirectorId() != null && Objects.equals(s.getDirectorId(), user.getId()))
                        || (s.getDirectorEmail() != null && s.getDirectorEmail().equalsIgnoreCase(user.getEmail()))
                        || (s.getDeanEmail() != null && s.getDeanEmail().equalsIgnoreCase(user.getEmail())))
                .findFirst().orElse(null);

        boolean isDirectorEligible = hasExplicitRoles
                ? explicitRoles.contains("DIRECTOR")
                : (user.getRole() == UserRole.DIRECTOR || matchedSchool != null);

        List<com.dypiu.nba.entity.Department> allDepts = departmentRepository.findAll();
        com.dypiu.nba.entity.Department matchedDept = allDepts.stream()
                .filter(d -> (user.getDepartmentId() != null && user.getDepartmentId().equalsIgnoreCase(d.getId()))
                        || (d.getHodEmail() != null && d.getHodEmail().equalsIgnoreCase(user.getEmail()))
                        || (d.getHodName() != null && d.getHodName().equalsIgnoreCase(user.getName()))
                        || (d.getHod() != null && d.getHod().equalsIgnoreCase(user.getName())))
                .findFirst().orElse(null);

        boolean isHodEligible = hasExplicitRoles
                ? explicitRoles.contains("HOD")
                : (user.getRole() == UserRole.HOD || matchedDept != null);

        List<com.dypiu.nba.entity.ProgrammeBatch> allBatches = programmeBatchRepository.findAll();
        com.dypiu.nba.entity.ProgrammeBatch matchedBatch = allBatches.stream()
                .filter(b -> (b.getCoordinatorEmail() != null && b.getCoordinatorEmail().equalsIgnoreCase(user.getEmail()))
                        || (b.getCoordinator() != null && b.getCoordinator().equalsIgnoreCase(user.getName()))
                        || (b.getCoordinatorId() != null && Objects.equals(b.getCoordinatorId(), user.getId()))
                        || (user.getMasterProgrammeId() != null && user.getMasterProgrammeId().equals(b.getMasterProgrammeId())))
                .findFirst().orElse(null);

        boolean isPcEligible = hasExplicitRoles
                ? (explicitRoles.contains("PROGRAMME_COORDINATOR") || explicitRoles.contains("PC"))
                : (user.getRole() == UserRole.PROGRAMME_COORDINATOR || matchedBatch != null);

        List<com.dypiu.nba.entity.ProgrammeBatchCourse> courses = programmeBatchCourseRepository.findAll();
        int assignedCount = (int) courses.stream().filter(c -> c.getDeletedAt() == null && (
                (c.getCourseCoordinatorId() != null && Objects.equals(c.getCourseCoordinatorId(), user.getId()))
                || (c.getCourseCoordinatorName() != null && c.getCourseCoordinatorName().equalsIgnoreCase(user.getName()))
                || (c.getAssignedFaculty() != null && (c.getAssignedFaculty().contains(user.getEmail()) || c.getAssignedFaculty().contains(user.getName())))
        )).count();

        boolean isCcEligible = hasExplicitRoles
                ? (explicitRoles.contains("COURSE_COORDINATOR") || explicitRoles.contains("FACULTY") || explicitRoles.contains("CC"))
                : (user.getRole() == UserRole.FACULTY || assignedCount > 0);

        List<UserProfileRoleDto> profiles = new ArrayList<>();

        // 1. IQAC Profile (Max 1)
        if (isIqacEligible) {
            boolean isCur = "IQAC".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("IQAC")
                    .roleCode("IQAC")
                    .title("IQAC Administrator")
                    .displayName("IQAC Administrator")
                    .description("Institution-wide administrative & quality assurance authority")
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
        }

        // 2. DIRECTOR Profile (Max 1)
        if (isDirectorEligible) {
            String sId = matchedSchool != null ? matchedSchool.getId() : user.getSchoolId();
            String sName = matchedSchool != null ? matchedSchool.getName() : null;
            boolean isCur = "DIRECTOR".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("DIRECTOR")
                    .roleCode("DIRECTOR")
                    .title("Director / Dean")
                    .displayName(sName != null ? "Director (" + sName + ")" : "Director / Dean")
                    .description("School-wide executive authority" + (sName != null ? " for " + sName : ""))
                    .schoolId(sId)
                    .schoolName(sName)
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
        }

        // 3. HOD Profile (Max 1)
        if (isHodEligible) {
            String dId = matchedDept != null ? matchedDept.getId() : user.getDepartmentId();
            String dName = matchedDept != null ? matchedDept.getName() : null;
            String sId = matchedDept != null ? matchedDept.getSchoolId() : user.getSchoolId();
            boolean isCur = "HOD".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("HOD")
                    .roleCode("HOD")
                    .title("Head of Department")
                    .displayName(dName != null ? "Head of Department (" + dName + ")" : "Head of Department")
                    .description("Department-wide curriculum and batch management" + (dName != null ? " for " + dName : ""))
                    .schoolId(sId)
                    .departmentId(dId)
                    .departmentName(dName)
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
        }

        // 4. PROGRAMME_COORDINATOR Profile (Max 1)
        if (isPcEligible) {
            String pBatchId = matchedBatch != null ? matchedBatch.getId() : null;
            String pBatchName = matchedBatch != null ? matchedBatch.getName() : null;
            String mProgId = matchedBatch != null ? matchedBatch.getMasterProgrammeId() : user.getMasterProgrammeId();
            String dId = user.getDepartmentId();
            if (mProgId != null) {
                dId = masterProgrammeRepository.findById(mProgId)
                        .map(com.dypiu.nba.entity.MasterProgramme::getDepartmentId)
                        .orElse(user.getDepartmentId());
            }

            boolean isCur = "PROGRAMME_COORDINATOR".equalsIgnoreCase(activeRole) || "PC".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("PROGRAMME_COORDINATOR")
                    .roleCode("PROGRAMME_COORDINATOR")
                    .title("Programme Coordinator")
                    .displayName(pBatchName != null ? "Programme Coordinator (" + pBatchName + ")" : "Programme Coordinator")
                    .description("Programme batch target configuration & OBE attainment tracking")
                    .departmentId(dId)
                    .masterProgrammeId(mProgId)
                    .programmeBatchId(pBatchId)
                    .programmeBatchName(pBatchName)
                    .schoolId(user.getSchoolId())
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
        }

        // 5. COURSE_COORDINATOR / FACULTY Profile (Max 1)
        if (isCcEligible) {
            boolean isCur = "COURSE_COORDINATOR".equalsIgnoreCase(activeRole) || "FACULTY".equalsIgnoreCase(activeRole) || "CC".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("COURSE_COORDINATOR")
                    .roleCode("COURSE_COORDINATOR")
                    .title("Course Coordinator")
                    .displayName("Course Coordinator / Faculty")
                    .description("Course outcomes, assessment targets, mark entry & course-level ATR")
                    .assignedCoursesCount(assignedCount)
                    .departmentId(user.getDepartmentId())
                    .schoolId(user.getSchoolId())
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
        }

        // If no profiles discovered, at least include user's primary role (Max 1)
        if (profiles.isEmpty()) {
            profiles.add(UserProfileRoleDto.builder()
                    .role(user.getRole().name())
                    .roleCode(user.getRole().name())
                    .title(user.getRole().name())
                    .displayName(user.getRole().name())
                    .description("Default user profile")
                    .departmentId(user.getDepartmentId())
                    .schoolId(user.getSchoolId())
                    .isActive(true)
                    .isCurrent(true)
                    .build());
        }

        return UserRolesResponseDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .primaryRole(user.getRole().name())
                .activeRole(activeRole)
                .profiles(profiles)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse switchRole(SwitchRoleRequestDto request, java.security.Principal principal) {
        String identifier = principal != null ? principal.getName() : null;
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Principal cannot be empty");
        }

        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseGet(() -> userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new BadRequestException("User not found: " + identifier)));

        String rawRole = request.getRole() != null ? request.getRole().trim().toUpperCase().replace("ROLE_", "") : null;
        if (rawRole == null || rawRole.isBlank()) {
            throw new BadRequestException("Role to switch to is required");
        }

        String targetRole = rawRole;
        if ("FACULTY".equals(targetRole)) {
            targetRole = "COURSE_COORDINATOR";
        }

        String schoolId = request.getSchoolId() != null && !request.getSchoolId().isBlank() ? request.getSchoolId() : user.getSchoolId();
        String departmentId = request.getDepartmentId() != null && !request.getDepartmentId().isBlank() ? request.getDepartmentId() : user.getDepartmentId();
        String masterProgrammeId = request.getMasterProgrammeId() != null && !request.getMasterProgrammeId().isBlank() ? request.getMasterProgrammeId() : user.getMasterProgrammeId();

        String accessToken = tokenProvider.generateTokenForUser(user.getUsername(), targetRole, schoolId, departmentId, masterProgrammeId);
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getJwtExpirationInMs())
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .role(targetRole)
                        .schoolId(schoolId)
                        .departmentId(departmentId)
                        .masterProgrammeId(masterProgrammeId)
                        .department(user.getDepartment())
                        .programme(user.getProgramme())
                        .build())
                .build();
    }

    private String resolveActiveRole(User user) {
        org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String activeRole = (String) attrs.getAttribute("ACTIVE_ROLE_OVERRIDE", org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (activeRole != null && !activeRole.isBlank()) {
                return activeRole.replace("ROLE_", "");
            }
        }
        return user.getRole() != null ? user.getRole().name() : "FACULTY";
    }

    private AuthResponse buildAuthResponse(User user) {
        String activeRole = user.getRole() != null ? user.getRole().name() : "FACULTY";
        String accessToken = tokenProvider.generateTokenForUser(user.getUsername(), activeRole, user.getSchoolId(), user.getDepartmentId(), user.getMasterProgrammeId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getJwtExpirationInMs())
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .role(activeRole)
                        .schoolId(user.getSchoolId())
                        .departmentId(user.getDepartmentId())
                        .masterProgrammeId(user.getMasterProgrammeId())
                        .department(user.getDepartment())
                        .programme(user.getProgramme())
                        .build())
                .build();
    }

}
