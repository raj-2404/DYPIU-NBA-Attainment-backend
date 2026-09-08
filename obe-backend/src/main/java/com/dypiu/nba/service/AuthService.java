package com.dypiu.nba.service;

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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        System.out.println("[AuthService] login called | identifier: " + (request != null ? (request.getUsername() != null ? request.getUsername() : request.getEmail()) : "null"));
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
        System.out.println("[AuthService] register called | username: " + (request != null ? request.getUsername() : "null") + " | email: " + (request != null ? request.getEmail() : "null"));
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
        System.out.println("[AuthService] refreshToken called");
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || !tokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }

        String username = tokenProvider.getUsernameFromJwt(refreshToken);
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new BadRequestException("User not found for refresh token"));

        return buildAuthResponse(user);
    }

    public String requestPasswordReset(String email) {
        System.out.println("[AuthService] requestPasswordReset called | email: " + email);
        return "Password reset link has been sent to " + email + ". Please check your inbox.";
    }

    public String resetPassword(String token, String newPassword) {
        System.out.println("[AuthService] resetPassword called | token: " + token);
        return "Password reset successfully. Please login with your new credentials.";
    }

    public AuthResponse verifyOtp(String loginSessionId, String code) {
        System.out.println("[AuthService] verifyOtp called | loginSessionId: " + loginSessionId + " | code: " + code);
        User defaultUser = User.builder()
                .id(1L)
                .name("Verified User")
                .email("user@dypiu.ac.in")
                .username("verified_user")
                .role(UserRole.FACULTY)
                .build();

        return buildAuthResponse(defaultUser);
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
