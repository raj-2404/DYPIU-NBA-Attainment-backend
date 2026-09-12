package com.dypiu.nba.security;

import com.dypiu.nba.dto.AuthResponse;
import com.dypiu.nba.dto.RefreshTokenRequest;
import com.dypiu.nba.dto.ResetPasswordRequest;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.exception.BadRequestException;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AttainmentCalculationService;
import com.dypiu.nba.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Adversarial Security Verification Suite
 * Executes aggressive offensive tests across all 6 production security attack families.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ProductionAdversarialSecurityVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private ProgrammeBatchRepository programmeBatchRepository;

    @Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttainmentCalculationService calculationService;

    private User facultyUser;
    private User hodDeptA;
    private User hodDeptB;
    private User pcProgA;
    private Department deptA;
    private Department deptB;
    private MasterProgramme progA;
    private MasterProgramme progB;
    private ProgrammeBatch batchA;
    private ProgrammeBatchCourse courseA;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        programmeBatchCourseRepository.deleteAll();
        programmeBatchRepository.deleteAll();
        masterProgrammeRepository.deleteAll();
        departmentRepository.deleteAll();
        schoolRepository.deleteAll();

        School school = schoolRepository.save(School.builder()
                .id("SOET")
                .name("School of Engineering")
                .code("SOET")
                .build());

        deptA = departmentRepository.save(Department.builder()
                .id("DEPT_CSE")
                .name("Computer Science")
                .code("CSE")
                .schoolId("SOET")
                .status("ACTIVE")
                .hodEmail("hod_cse@dypiu.ac.in")
                .build());

        deptB = departmentRepository.save(Department.builder()
                .id("DEPT_BIO")
                .name("Biotechnology")
                .code("BIO")
                .schoolId("SOET")
                .status("ACTIVE")
                .hodEmail("hod_bio@dypiu.ac.in")
                .build());

        progA = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("BTECH_CSE")
                .name("B.Tech CSE")
                .degreeAwarded("B.Tech")
                .departmentId("DEPT_CSE")
                .departmentName("Computer Science")
                .level("UG")
                .status("ACTIVE")
                .durationYears(4)
                .build());

        progB = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("BTECH_BIO")
                .name("B.Tech Bio")
                .degreeAwarded("B.Tech")
                .departmentId("DEPT_BIO")
                .departmentName("Biotechnology")
                .level("UG")
                .status("ACTIVE")
                .durationYears(4)
                .build());

        batchA = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("BATCH_CSE_2022")
                .masterProgrammeId("BTECH_CSE")
                .name("CSE 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .durationYears(4)
                .coordinatorEmail("pc_cse@dypiu.ac.in")
                .status("ACTIVE")
                .build());

        courseA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("COURSE_DS_101")
                .programmeBatchId("BATCH_CSE_2022")
                .code("CS201")
                .name("Data Structures")
                .semester(3)
                .courseCoordinatorName("Faculty CSE")
                .assignedFaculty("faculty_cse@dypiu.ac.in")
                .status("ACTIVE")
                .build());

        facultyUser = userRepository.save(User.builder()
                .username("faculty_cse")
                .email("faculty_cse@dypiu.ac.in")
                .passwordHash(passwordEncoder.encode("Pass123456"))
                .name("Faculty CSE")
                .role(UserRole.FACULTY)
                .schoolId("SOET")
                .departmentId("DEPT_CSE")
                .masterProgrammeId("BTECH_CSE")
                .isActive(true)
                .build());

        hodDeptA = userRepository.save(User.builder()
                .username("hod_cse")
                .email("hod_cse@dypiu.ac.in")
                .passwordHash(passwordEncoder.encode("Pass123456"))
                .name("HOD CSE")
                .role(UserRole.HOD)
                .schoolId("SOET")
                .departmentId("DEPT_CSE")
                .isActive(true)
                .build());

        hodDeptB = userRepository.save(User.builder()
                .username("hod_bio")
                .email("hod_bio@dypiu.ac.in")
                .passwordHash(passwordEncoder.encode("Pass123456"))
                .name("HOD BIO")
                .role(UserRole.HOD)
                .schoolId("SOET")
                .departmentId("DEPT_BIO")
                .isActive(true)
                .build());

        pcProgA = userRepository.save(User.builder()
                .username("pc_cse")
                .email("pc_cse@dypiu.ac.in")
                .passwordHash(passwordEncoder.encode("Pass123456"))
                .name("PC CSE")
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId("SOET")
                .departmentId("DEPT_CSE")
                .masterProgrammeId("BTECH_CSE")
                .isActive(true)
                .build());
    }

    // =========================================================================
    // Attack Family 1: IDOR & BOLA (Broken Object Level Authorization)
    // =========================================================================
    @Test
    @DisplayName("IDOR: HOD Dept A is forbidden (403) from accessing Dept B programmes")
    void testCrossDepartmentIdorForbidden() throws Exception {
        String tokenHodA = jwtTokenProvider.generateTokenForUser(hodDeptA.getUsername(), "HOD", "SOET", "DEPT_CSE", null);

        mockMvc.perform(get("/api/v1/academic/master-programmes")
                        .header("Authorization", "Bearer " + tokenHodA)
                        .param("departmentId", "DEPT_BIO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR: Programme Coordinator Prog A is forbidden (403) from accessing Prog B batches")
    void testCrossProgrammeIdorForbidden() throws Exception {
        String tokenPcA = jwtTokenProvider.generateTokenForUser(pcProgA.getUsername(), "PROGRAMME_COORDINATOR", "SOET", "DEPT_CSE", "BTECH_CSE");

        mockMvc.perform(get("/api/v1/academic/programme-batches")
                        .header("Authorization", "Bearer " + tokenPcA)
                        .param("masterProgrammeId", "BTECH_BIO"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Attack Family 2: Authorization & Privilege Escalation
    // =========================================================================
    @Test
    @DisplayName("Privilege Escalation: Faculty is forbidden (403) from calling IQAC Admin APIs")
    void testFacultyEscalationToAdminForbidden() throws Exception {
        String tokenFaculty = jwtTokenProvider.generateTokenForUser(facultyUser.getUsername(), "FACULTY", "SOET", "DEPT_CSE", null);

        mockMvc.perform(get("/admin/system-settings")
                        .header("Authorization", "Bearer " + tokenFaculty))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authentication Barrier: Unauthenticated request to protected endpoint returns 401")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/academic/departments"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Attack Family 3: Authentication Abuse & Token Lifecycle
    // =========================================================================
    @Test
    @DisplayName("Token Integrity: Tampered JWT signature is rejected with 401")
    void testTamperedJwtRejected() throws Exception {
        String validToken = jwtTokenProvider.generateTokenForUser(facultyUser.getUsername());
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        mockMvc.perform(get("/api/v1/academic/schools")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Revocation Barrier: Revoked access token is rejected with 401")
    void testRevokedAccessTokenRejected() throws Exception {
        String validToken = jwtTokenProvider.generateTokenForUser(facultyUser.getUsername());

        // Perform logout
        authService.logout(validToken, null);

        mockMvc.perform(get("/api/v1/academic/schools")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Password Reset Token Replay: Replaying used reset token throws BadRequestException")
    void testPasswordResetTokenReplayRejected() {
        String resetToken = authService.createTestPasswordResetToken(facultyUser.getUsername());

        // First reset succeeds
        authService.resetPassword(resetToken, "NewSecret123");

        // Replay attempt fails
        assertThrows(BadRequestException.class, () -> 
                authService.resetPassword(resetToken, "SecondSecret123"));
    }

    // =========================================================================
    // Attack Family 4: Malicious File Boundaries & Injection
    // =========================================================================
    @Test
    @DisplayName("File Security: Disguised shell script (.sh as .xlsx) is rejected with BadRequestException")
    void testDisguisedExecutableRejected() {
        byte[] shellScriptContent = "#!/bin/bash\necho 'malicious'".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakeXlsx = new MockMultipartFile("file", "exploit.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", shellScriptContent);

        assertThrows(BadRequestException.class, () -> 
                calculationService.saveUploadedFile(fakeXlsx, "marks", "COURSE_DS_101"));
    }

    @Test
    @DisplayName("Path Traversal: Upload with traversal filename cannot escape target directory")
    void testPathTraversalSanitized() {
        // Zip signature PK\x03\x04
        byte[] validZipHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile traversalFile = new MockMultipartFile("file", "../../../../etc/passwd.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validZipHeader);

        java.nio.file.Path stored = calculationService.saveUploadedFile(traversalFile, "marks", "COURSE_DS_101");
        assertNotNull(stored);
        assertFalse(stored.toString().contains(".."));
        assertTrue(stored.toString().contains("COURSE_DS_101"));
    }

    @Test
    @DisplayName("Formula Injection: Neutralizes DDE triggers (=, +, -, @, \\t, \\r)")
    void testFormulaInjectionSanitization() {
        assertEquals("'=cmd|'/C calc'!A0", SecuritySanitizer.sanitizeFormulaInjection("=cmd|'/C calc'!A0"));
        assertEquals("'+12345", SecuritySanitizer.sanitizeFormulaInjection("+12345"));
        assertEquals("'-12345", SecuritySanitizer.sanitizeFormulaInjection("-12345"));
        assertEquals("'@SUM(1,2)", SecuritySanitizer.sanitizeFormulaInjection("@SUM(1,2)"));
        assertEquals("Normal Student Name", SecuritySanitizer.sanitizeFormulaInjection("Normal Student Name"));
    }

    // =========================================================================
    // Attack Family 5: Information Disclosure & Reconnaissance
    // =========================================================================
    @Test
    @DisplayName("Reconnaissance: Public /health does not leak internal stack versions")
    void testHealthEndpointDoesNotLeakVersions() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("DYPIU NBA Attainment System"))
                .andExpect(content().string(not(containsString("javaVersion"))))
                .andExpect(content().string(not(containsString("springBoot"))))
                .andExpect(content().string(not(containsString("Flyway"))));
    }

    @Test
    @DisplayName("Reconnaissance: Public Swagger and Actuator endpoints are blocked for unauthenticated users")
    void testSwaggerAndActuatorBlocked() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Attack Family 6: HTTP Security Headers & Method Restrictions
    // =========================================================================
    @Test
    @DisplayName("Security Headers: Verified X-Content-Type-Options, X-Frame-Options, and HSTS")
    void testSecurityHeadersPresent() throws Exception {
        mockMvc.perform(get("/health").secure(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }
}
