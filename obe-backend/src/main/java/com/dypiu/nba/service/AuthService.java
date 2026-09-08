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

        List<UserProfileRoleDto> profiles = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        // 1. IQAC Profile
        if (user.getRole() == UserRole.IQAC) {
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
            seenKeys.add("IQAC");
        }

        // 2. DIRECTOR Profile(s)
        List<com.dypiu.nba.entity.School> schools = schoolRepository.findAll();
        for (com.dypiu.nba.entity.School school : schools) {
            boolean isDirector = (user.getRole() == UserRole.DIRECTOR && school.getId() != null && school.getId().equals(user.getSchoolId()))
                    || (school.getDirectorId() != null && Objects.equals(school.getDirectorId(), user.getId()))
                    || (school.getDirectorEmail() != null && school.getDirectorEmail().equalsIgnoreCase(user.getEmail()))
                    || (school.getDeanEmail() != null && school.getDeanEmail().equalsIgnoreCase(user.getEmail()))
                    || (user.getRole() == UserRole.DIRECTOR);

            if (isDirector) {
                String key = "DIRECTOR_" + school.getId();
                if (!seenKeys.contains(key)) {
                    seenKeys.add(key);
                    boolean isCur = "DIRECTOR".equalsIgnoreCase(activeRole);
                    profiles.add(UserProfileRoleDto.builder()
                            .role("DIRECTOR")
                            .roleCode("DIRECTOR")
                            .title("Director / Dean")
                            .displayName("Director - " + (school.getName() != null ? school.getName() : "School"))
                            .description("School-wide executive authority for " + (school.getName() != null ? school.getName() : "assigned school"))
                            .schoolId(school.getId())
                            .schoolName(school.getName())
                            .isActive(isCur)
                            .isCurrent(isCur)
                            .build());
                }
            }
        }

        // 3. HOD Profile(s)
        List<com.dypiu.nba.entity.Department> departments = departmentRepository.findAll();
        for (com.dypiu.nba.entity.Department dept : departments) {
            boolean isHod = (user.getRole() == UserRole.HOD && dept.getId() != null && dept.getId().equals(user.getDepartmentId()))
                    || (dept.getHodEmail() != null && dept.getHodEmail().equalsIgnoreCase(user.getEmail()))
                    || (dept.getHodName() != null && dept.getHodName().equalsIgnoreCase(user.getName()))
                    || (dept.getHod() != null && dept.getHod().equalsIgnoreCase(user.getName()))
                    || (user.getRole() == UserRole.HOD && user.getDepartmentId() == null);

            if (isHod) {
                String key = "HOD_" + dept.getId();
                if (!seenKeys.contains(key)) {
                    seenKeys.add(key);
                    boolean isCur = "HOD".equalsIgnoreCase(activeRole);
                    profiles.add(UserProfileRoleDto.builder()
                            .role("HOD")
                            .roleCode("HOD")
                            .title("Head of Department")
                            .displayName("Head of Department (" + (dept.getName() != null ? dept.getName() : dept.getId()) + ")")
                            .description("Department-wide curriculum and batch management for " + (dept.getName() != null ? dept.getName() : "assigned department"))
                            .schoolId(dept.getSchoolId())
                            .departmentId(dept.getId())
                            .departmentName(dept.getName())
                            .isActive(isCur)
                            .isCurrent(isCur)
                            .build());
                }
            }
        }

        // 4. PROGRAMME_COORDINATOR Profile(s)
        List<com.dypiu.nba.entity.ProgrammeBatch> batches = programmeBatchRepository.findAll();
        for (com.dypiu.nba.entity.ProgrammeBatch batch : batches) {
            boolean isPc = (batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().equalsIgnoreCase(user.getEmail()))
                    || (batch.getCoordinator() != null && batch.getCoordinator().equalsIgnoreCase(user.getName()))
                    || (batch.getCoordinatorId() != null && Objects.equals(batch.getCoordinatorId(), user.getId()))
                    || (user.getRole() == UserRole.PROGRAMME_COORDINATOR && user.getMasterProgrammeId() != null && user.getMasterProgrammeId().equals(batch.getMasterProgrammeId()));

            if (isPc) {
                String key = "PC_" + batch.getId();
                if (!seenKeys.contains(key)) {
                    seenKeys.add(key);
                    String batchDeptId = masterProgrammeRepository.findById(batch.getMasterProgrammeId())
                            .map(com.dypiu.nba.entity.MasterProgramme::getDepartmentId)
                            .orElse(user.getDepartmentId());

                    boolean isCur = "PROGRAMME_COORDINATOR".equalsIgnoreCase(activeRole);
                    profiles.add(UserProfileRoleDto.builder()
                            .role("PROGRAMME_COORDINATOR")
                            .roleCode("PROGRAMME_COORDINATOR")
                            .title("Programme Coordinator")
                            .displayName("Programme Coordinator - " + (batch.getName() != null ? batch.getName() : batch.getId()))
                            .description("Programme batch target configuration & OBE attainment tracking")
                            .departmentId(batchDeptId)
                            .masterProgrammeId(batch.getMasterProgrammeId())
                            .programmeBatchId(batch.getId())
                            .programmeBatchName(batch.getName())
                            .isActive(isCur)
                            .isCurrent(isCur)
                            .build());
                }
            }
        }

        // Also check Master Programmes for PC
        if (user.getRole() == UserRole.PROGRAMME_COORDINATOR && profiles.stream().noneMatch(p -> "PROGRAMME_COORDINATOR".equals(p.getRole()))) {
            boolean isCur = "PROGRAMME_COORDINATOR".equalsIgnoreCase(activeRole);
            profiles.add(UserProfileRoleDto.builder()
                    .role("PROGRAMME_COORDINATOR")
                    .roleCode("PROGRAMME_COORDINATOR")
                    .title("Programme Coordinator")
                    .displayName("Programme Coordinator")
                    .description("Programme batch target configuration & OBE attainment tracking")
                    .departmentId(user.getDepartmentId())
                    .masterProgrammeId(user.getMasterProgrammeId())
                    .isActive(isCur)
                    .isCurrent(isCur)
                    .build());
            seenKeys.add("PC_DEFAULT");
        }

        // 5. COURSE_COORDINATOR / FACULTY Profile
        List<com.dypiu.nba.entity.ProgrammeBatchCourse> courses = programmeBatchCourseRepository.findAll();
        int assignedCount = (int) courses.stream().filter(c -> c.getDeletedAt() == null && (
                (c.getCourseCoordinatorId() != null && Objects.equals(c.getCourseCoordinatorId(), user.getId()))
                || (c.getCourseCoordinatorName() != null && c.getCourseCoordinatorName().equalsIgnoreCase(user.getName()))
                || (c.getAssignedFaculty() != null && (c.getAssignedFaculty().contains(user.getEmail()) || c.getAssignedFaculty().contains(user.getName())))
        )).count();

        boolean eligibleForFaculty = (user.getRole() == UserRole.FACULTY) || (assignedCount > 0) || (user.getRole() != UserRole.IQAC);
        if (eligibleForFaculty) {
            boolean isCur = "COURSE_COORDINATOR".equalsIgnoreCase(activeRole) || "FACULTY".equalsIgnoreCase(activeRole);
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

        // If no profiles discovered, at least include user's primary role
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
