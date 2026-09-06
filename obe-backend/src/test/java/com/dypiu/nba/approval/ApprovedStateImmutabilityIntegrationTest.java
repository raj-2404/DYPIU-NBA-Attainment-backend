package com.dypiu.nba.approval;

import com.dypiu.nba.audit.AuditAction;
import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.JwtTokenProvider;
import com.dypiu.nba.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ApprovedStateImmutabilityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private ApprovalHistoryRepository approvalHistoryRepository;

    @Autowired
    private AttainmentConfigurationRepository configRepository;

    @Autowired
    private CourseAtrRepository courseAtrRepository;

    @Autowired
    private ProgrammeAtrRepository programmeAtrRepository;

    @Autowired
    private ProgrammeOutcomeRepository poRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository psoRepository;

    @Autowired
    private CourseOutcomeRepository coRepository;

    @Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Autowired
    private ProgrammeBatchRepository programmeBatchRepository;

    @Autowired
    private MasterCourseRepository masterCourseRepository;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User director;
    private User hod;
    private User pc;

    private String directorToken;
    private String hodToken;
    private String pcToken;

    private School school;
    private Department department;
    private MasterProgramme programme;
    private ProgrammeBatch batch;
    private MasterCourse course;
    private ProgrammeBatchCourse batchCourse;

    @BeforeEach
    void setUp() {
        approvalHistoryRepository.deleteAll();
        approvalRequestRepository.deleteAll();
        courseAtrRepository.deleteAll();
        programmeAtrRepository.deleteAll();
        configRepository.deleteAll();
        coRepository.deleteAll();
        poRepository.deleteAll();
        psoRepository.deleteAll();
        programmeBatchCourseRepository.deleteAll();
        programmeBatchRepository.deleteAll();
        masterCourseRepository.deleteAll();
        masterProgrammeRepository.deleteAll();
        departmentRepository.deleteAll();
        schoolRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();

        school = schoolRepository.save(School.builder()
                .id("sch-soe-imm")
                .code("SOE-IMM")
                .name("School of Engineering Immutability")
                .build());

        department = departmentRepository.save(Department.builder()
                .id("dept-cse-imm")
                .schoolId(school.getId())
                .code("CSE-IMM")
                .name("Computer Science Immutability")
                .build());

        programme = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-btech-imm")
                .departmentId(department.getId())
                .code("BTECH-CSE-IMM")
                .name("B.Tech CSE Immutability")
                .durationYears(4)
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-imm")
                .masterProgrammeId(programme.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .coordinatorEmail("pc.imm@dypiu.ac.in")
                .build());

        course = masterCourseRepository.save(MasterCourse.builder()
                .id("crs-cs101-imm")
                .masterProgrammeId(programme.getId())
                .code("CS101-IMM")
                .name("Computer Programming")
                .credits(4)
                .build());

        batchCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-cs101-imm")
                .programmeBatchId(batch.getId())
                .masterCourseId(course.getId())
                .semester(1)
                .build());

        director = createUser("director.imm@dypiu.ac.in", "dir_imm", UserRole.DIRECTOR, school.getId(), null, null);
        hod = createUser("hod.imm@dypiu.ac.in", "hod_imm", UserRole.HOD, school.getId(), department.getId(), null);
        pc = createUser("pc.imm@dypiu.ac.in", "pc_imm", UserRole.PROGRAMME_COORDINATOR, school.getId(), department.getId(), programme.getId());

        directorToken = generateToken(director);
        hodToken = generateToken(hod);
        pcToken = generateToken(pc);
    }

    private User createUser(String email, String username, UserRole role, String schoolId, String deptId, String progId) {
        return userRepository.save(User.builder()
                .email(email)
                .username(username)
                .name(username)
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .role(role)
                .schoolId(schoolId)
                .departmentId(deptId)
                .masterProgrammeId(progId)
                .isActive(true)
                .build());
    }

    private String generateToken(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        return jwtTokenProvider.generateToken(auth);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void testAttainmentConfigImmutabilityWhenApproved() {
        // 1. Initial save in DRAFT
        AttainmentConfiguration cfg = AttainmentConfiguration.builder()
                .programmeBatchCourseId(batchCourse.getId())
                .directWeight(new BigDecimal("80.00"))
                .indirectWeight(new BigDecimal("20.00"))
                .directThreshold(new BigDecimal("60.00"))
                .indirectThreshold(new BigDecimal("60.00"))
                .status(AttainmentConfigStatus.DRAFT)
                .build();

        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/attainment/config/" + batchCourse.getId(), HttpMethod.POST, new HttpEntity<>(cfg, authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Attainment Configuration
        approvalService.verifyStatus(batchCourse.getId(), "configStatus", "APPROVED", "Approved by HOD", hod.getName());
        assertTrue(approvalService.isAttainmentConfigApproved(batchCourse.getId()));

        // 3. Modification while APPROVED is allowed and resets status to DRAFT
        cfg.setDirectThreshold(new BigDecimal("70.00"));
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/attainment/config/" + batchCourse.getId(), HttpMethod.POST, new HttpEntity<>(cfg, authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
        assertFalse(approvalService.isAttainmentConfigApproved(batchCourse.getId()));
    }

    @Test
    void testCourseOutcomesImmutabilityWhenApproved() {
        CourseOutcome co1 = CourseOutcome.builder()
                .code("CO1")
                .statement("Understand data structures")
                .targetLevel(new BigDecimal("2.50"))
                .build();

        // 1. Initial save in DRAFT
        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/outcomes/master-courses/" + batchCourse.getId() + "/cos", HttpMethod.POST, new HttpEntity<>(List.of(co1), authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Course Outcomes
        approvalService.verifyStatus(batchCourse.getId(), "coStatus", "APPROVED", "COs approved by HOD", hod.getName());
        assertTrue(approvalService.isCoDefinitionApproved(batchCourse.getId()));

        // 3. Modification while APPROVED is allowed and resets status to DRAFT
        co1.setStatement("Modified statement after approval");
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/outcomes/master-courses/" + batchCourse.getId() + "/cos", HttpMethod.POST, new HttpEntity<>(List.of(co1), authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
        assertFalse(approvalService.isCoDefinitionApproved(batchCourse.getId()));
    }

    @Test
    void testCourseAtrImmutabilityWhenApproved() {
        CourseAtr atr = CourseAtr.builder()
                .programmeBatchCourseId(batchCourse.getId())
                .coCode("CO1")
                .statement("CO1 statement")
                .targetScore(new BigDecimal("2.50"))
                .actualScore(new BigDecimal("2.60"))
                .pctAchieved(new BigDecimal("100.00"))
                .actionsJson("[\"Tutorials conducted\"]")
                .status(CourseAtrStatus.DRAFT)
                .build();

        // 1. Initial save in DRAFT
        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/atr/master-courses/" + batchCourse.getId(), HttpMethod.POST, new HttpEntity<>(List.of(atr), authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Course ATR
        approvalService.verifyStatus(batchCourse.getId(), "atrStatus", "APPROVED", "Course ATR approved by HOD", hod.getName());
        assertTrue(approvalService.isCourseAtrApproved(batchCourse.getId()));

        // 3. Modification while APPROVED is allowed and resets status to DRAFT
        atr.setActionsJson("[\"Modified action\"]");
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/atr/master-courses/" + batchCourse.getId(), HttpMethod.POST, new HttpEntity<>(List.of(atr), authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
        assertFalse(approvalService.isCourseAtrApproved(batchCourse.getId()));
    }

    @Test
    void testCourseMappingMatrixImmutabilityWhenApproved() {
        CourseMappingMatrixDto matrixDto = CourseMappingMatrixDto.builder()
                .masterProgrammeId(programme.getId())
                .masterCourseId(batchCourse.getId())
                .matrix(Map.of("CO1", Map.of("PO1", 3)))
                .build();

        // 1. Initial save in DRAFT
        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/outcomes/master-courses/" + batchCourse.getId() + "/mappings", HttpMethod.POST, new HttpEntity<>(matrixDto, authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Course Mappings
        approvalService.verifyStatus(batchCourse.getId(), "coStatus", "APPROVED", "Mappings approved by HOD", hod.getName());
        assertTrue(approvalService.isCoDefinitionApproved(batchCourse.getId()));

        // 3. Modification while APPROVED is allowed and resets status to DRAFT
        matrixDto.setMatrix(Map.of("CO1", Map.of("PO1", 2)));
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/outcomes/master-courses/" + batchCourse.getId() + "/mappings", HttpMethod.POST, new HttpEntity<>(matrixDto, authHeaders(pcToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
        assertFalse(approvalService.isCoDefinitionApproved(batchCourse.getId()));
    }

    @Test
    void testCourseAllocationImmutabilityWhenApproved() {
        Map<String, Object> alloc = Map.of(
                "masterCourseId", course.getId(),
                "coordinator", "Dr. Alan Turing",
                "coordinatorEmail", "alan@dypiu.ac.in"
        );

        Map<String, Object> body = Map.of(
                "masterProgrammeId", programme.getId(),
                "programmeBatchId", batch.getId(),
                "allocations", List.of(alloc),
                "submit", false
        );

        // 1. Initial save in DRAFT
        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/academic/master-courses/allocate",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Course Allocation (by HOD)
        String sem1Key = "allocation-" + batch.getId() + "-sem-1";
        approvalService.verifyStatus(sem1Key, "allocationStatus", "APPROVED", "Allocation approved by HOD", hod.getName());
        assertTrue(approvalService.isAllocationApproved(batch.getId(), 1));

        // 3. Attempt mutation while APPROVED -> MUST BE REJECTED WITH 409 CONFLICT
        ResponseEntity<ApiResponse> conflictRes = restTemplate.exchange(
                "/academic/master-courses/allocate",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.CONFLICT, conflictRes.getStatusCode());

        // 4. Request Revision
        approvalService.requestRevisionStatus(sem1Key, "allocationStatus", "REVISION_REQUESTED", "Change coordinator assignment", hod.getName());
        assertFalse(approvalService.isAllocationApproved(batch.getId(), 1));

        // 5. Modification allowed after revision request
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/academic/master-courses/allocate",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
    }

    @Test
    void testProgrammeAtrImmutabilityWhenApproved() {
        ProgrammeAtr atr = ProgrammeAtr.builder()
                .programmeBatchId(batch.getId())
                .status(ProgrammeAtrStatus.DRAFT)
                .build();

        // 1. Initial save in DRAFT
        ResponseEntity<ApiResponse> draftRes = restTemplate.exchange(
                "/atr/master-programmes/" + programme.getId(),
                HttpMethod.POST,
                new HttpEntity<>(atr, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.OK, draftRes.getStatusCode());

        // 2. Approve Programme ATR
        approvalService.verifyStatus("prog-atr-" + batch.getId(), "programmeAtrStatus", "APPROVED", "Programme ATR approved by Director", director.getName());
        assertTrue(approvalService.isProgrammeAtrApproved(batch.getId()));

        // 3. Attempt mutation while APPROVED -> MUST BE REJECTED WITH 409 CONFLICT
        ResponseEntity<ApiResponse> conflictRes = restTemplate.exchange(
                "/atr/master-programmes/" + programme.getId(),
                HttpMethod.POST,
                new HttpEntity<>(atr, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.CONFLICT, conflictRes.getStatusCode());

        // 4. Request Revision
        approvalService.requestRevisionStatus("prog-atr-" + batch.getId(), "programmeAtrStatus", "REVISION_REQUESTED", "Revise PO attainment actions", director.getName());
        assertFalse(approvalService.isProgrammeAtrApproved(batch.getId()));

        // 5. Modification allowed after revision request
        ResponseEntity<ApiResponse> modRes = restTemplate.exchange(
                "/atr/master-programmes/" + programme.getId(),
                HttpMethod.POST,
                new HttpEntity<>(atr, authHeaders(pcToken)),
                ApiResponse.class
        );
        assertEquals(HttpStatus.OK, modRes.getStatusCode());
    }
}
