package com.dypiu.nba.security;

import com.dypiu.nba.controller.ApprovalController;
import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.ApprovalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ApprovalWorkflowSecurityTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalController approvalController;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private ApprovalHistoryRepository approvalHistoryRepository;

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
    private UserRepository userRepository;

    private School schoolA;
    private School schoolB;
    private Department deptA;
    private Department deptB;
    private MasterProgramme progA;
    private MasterProgramme progB;
    private ProgrammeBatch batchA;
    private ProgrammeBatchCourse courseA;
    private ProgrammeBatchCourse offeringA;

    private User directorA;
    private User directorB;
    private User hodA;
    private User hodB;
    private User pcA;
    private User pcB;
    private User facultyA;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        long salt = System.nanoTime();

        // 1. Setup School A hierarchy
        schoolA = schoolRepository.save(School.builder()
                .id("sch-app-a-" + salt)
                .name("School of Engineering A " + salt)
                .code("SOEA" + salt)
                .directorEmail("director.a." + salt + "@dypiu.ac.in")
                .build());

        deptA = departmentRepository.save(Department.builder()
                .id("dept-app-a-" + salt)
                .name("Computer Science A " + salt)
                .code("CSA" + salt)
                .schoolId(schoolA.getId())
                .hodEmail("hod.a." + salt + "@dypiu.ac.in")
                .build());

        progA = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-app-a-" + salt)
                .name("B.Tech CS A " + salt)
                .code("BTCSA" + salt)
                .departmentId(deptA.getId())
                .coordinatorEmail("pc.a." + salt + "@dypiu.ac.in")
                .build());

        batchA = programmeBatchRepository.save(ProgrammeBatch.builder().id("batch-app-a-" + salt).masterProgrammeId(progA.getId())
                .name("2022-2026")
                .startYear(2022)
                .endYear(2026)
                .coordinatorEmail("pc.a." + salt + "@dypiu.ac.in")
                .build());

        courseA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA.getId()).semester(1)
                .id("crs-app-a-" + salt)
                .code("CS" + salt)
                .name("Data Structures A")
                .masterProgrammeId(progA.getId())
                .credits(4)
                .courseType("THEORY")
                .build());

        offeringA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-app-a-" + salt)
                .masterCourseId(courseA.getId())
                .programmeBatchId(batchA.getId())
                .semester(1)
                .assignedFaculty("faculty.a." + salt + "@dypiu.ac.in")
                .build());

        // 2. Setup School B hierarchy
        schoolB = schoolRepository.save(School.builder()
                .id("sch-app-b-" + salt)
                .name("School of Management B " + salt)
                .code("SOMB" + salt)
                .directorEmail("director.b." + salt + "@dypiu.ac.in")
                .build());

        deptB = departmentRepository.save(Department.builder()
                .id("dept-app-b-" + salt)
                .name("Management B " + salt)
                .code("MGTB" + salt)
                .schoolId(schoolB.getId())
                .hodEmail("hod.b." + salt + "@dypiu.ac.in")
                .build());

        progB = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-app-b-" + salt)
                .name("MBA B " + salt)
                .code("MBAB" + salt)
                .departmentId(deptB.getId())
                .coordinatorEmail("pc.b." + salt + "@dypiu.ac.in")
                .build());

        // 3. Create Users
        directorA = userRepository.save(User.builder()
                .username("director.a." + salt + "@dypiu.ac.in")
                .email("director.a." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Director Alpha " + salt)
                .role(UserRole.DIRECTOR)
                .schoolId(schoolA.getId())
                .isActive(true)
                .build());

        directorB = userRepository.save(User.builder()
                .username("director.b." + salt + "@dypiu.ac.in")
                .email("director.b." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Director Beta " + salt)
                .role(UserRole.DIRECTOR)
                .schoolId(schoolB.getId())
                .isActive(true)
                .build());

        hodA = userRepository.save(User.builder()
                .username("hod.a." + salt + "@dypiu.ac.in")
                .email("hod.a." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("HOD Alpha " + salt)
                .role(UserRole.HOD)
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .isActive(true)
                .build());

        hodB = userRepository.save(User.builder()
                .username("hod.b." + salt + "@dypiu.ac.in")
                .email("hod.b." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("HOD Beta " + salt)
                .role(UserRole.HOD)
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .isActive(true)
                .build());

        pcA = userRepository.save(User.builder()
                .username("pc.a." + salt + "@dypiu.ac.in")
                .email("pc.a." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("PC Alpha " + salt)
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .isActive(true)
                .build());

        pcB = userRepository.save(User.builder()
                .username("pc.b." + salt + "@dypiu.ac.in")
                .email("pc.b." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("PC Beta " + salt)
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .isActive(true)
                .build());

        facultyA = userRepository.save(User.builder()
                .username("faculty.a." + salt + "@dypiu.ac.in")
                .email("faculty.a." + salt + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Faculty Alpha " + salt)
                .role(UserRole.FACULTY)
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getEmail(), "pass", authorities);
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // =========================================================================
    // 1. DIRECTOR TESTS
    // =========================================================================

    @Test
    @DisplayName("1. Director A gets own school approvals successfully")
    void testDirectorA_GetsOwnApprovals() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-a-1-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework A")
                .resourceId("res-a")
                .schoolId(schoolA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-A")
                .build());

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-b-1-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        ResponseEntity<ApiResponse<List<ApprovalRequest>>> res = approvalController.getApprovals(null, null, null, null, null);
        assertNotNull(res.getBody());
        List<ApprovalRequest> list = res.getBody().getData();
        assertTrue(list.stream().anyMatch(a -> a.getId().equals(reqA.getId())));
        assertFalse(list.stream().anyMatch(a -> a.getId().equals(reqB.getId())));
    }

    @Test
    @DisplayName("2. Director A requests School B approvals explicitly -> 403 Forbidden")
    void testDirectorA_RequestsSchoolBApprovals_Throws403() {
        setAuthenticatedUser(directorA);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.getDirectorApprovals(schoolB.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("3. Director A gets School B approval by ID -> 403 Forbidden")
    void testDirectorA_GetSchoolBApprovalById_Throws403() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-b-2-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.getApprovalById(reqB.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("4. Director A approves School B request -> 403 Forbidden")
    void testDirectorA_ApproveSchoolBRequest_Throws403() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-b-3-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqB.getId(), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("5. Director A rejects School B request -> 403 Forbidden")
    void testDirectorA_RejectSchoolBRequest_Throws403() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-b-4-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.rejectRequest(reqB.getId(), Map.of("remarks", "Rejected")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("6. Director A gets School B history -> 403 Forbidden")
    void testDirectorA_GetSchoolBHistory_Throws403() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-b-5-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.getApprovalHistory(reqB.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // =========================================================================
    // 2. HOD & PC TESTS
    // =========================================================================

    @Test
    @DisplayName("7. HOD A gets own department approvals -> 200")
    void testHodA_GetsOwnDepartmentApprovals() {
        setAuthenticatedUser(hodA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-hod-a-" + System.nanoTime())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("MasterCourse Allocation A")
                .resourceId("allocation-" + progA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("pc.a@dypiu.ac.in")
                .build());

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-hod-b-" + System.nanoTime())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("MasterCourse Allocation B")
                .resourceId("allocation-" + progB.getId())
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("pc.b@dypiu.ac.in")
                .build());

        ResponseEntity<ApiResponse<List<ApprovalRequest>>> res = approvalController.getApprovals(null, null, null, null, null);
        assertNotNull(res.getBody());
        List<ApprovalRequest> list = res.getBody().getData();
        assertTrue(list.stream().anyMatch(a -> a.getId().equals(reqA.getId())));
        assertFalse(list.stream().anyMatch(a -> a.getId().equals(reqB.getId())));
    }

    @Test
    @DisplayName("8. HOD A accesses another department approval -> 403 Forbidden")
    void testHodA_AccessesAnotherDepartmentApproval_Throws403() {
        setAuthenticatedUser(hodA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-hod-b-2-" + System.nanoTime())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("MasterCourse Allocation B")
                .resourceId("allocation-" + progB.getId())
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("pc.b@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.getApprovalById(reqB.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("9. HOD A approves another department approval -> 403 Forbidden")
    void testHodA_ApprovesAnotherDepartmentApproval_Throws403() {
        setAuthenticatedUser(hodA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-hod-b-3-" + System.nanoTime())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("MasterCourse Allocation B")
                .resourceId("allocation-" + progB.getId())
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("pc.b@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqB.getId(), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("10. PC A accesses own programme approval -> 200")
    void testPcA_AccessesOwnProgrammeApproval() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-pc-a-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.a@dypiu.ac.in")
                .build());

        ResponseEntity<ApiResponse<ApprovalRequest>> res = approvalController.getApprovalById(reqA.getId());
        assertNotNull(res.getBody());
        assertEquals(reqA.getId(), res.getBody().getData().getId());
    }

    @Test
    @DisplayName("11. PC A accesses another programme approval -> 403 Forbidden")
    void testPcA_AccessesAnotherProgrammeApproval_Throws403() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-pc-b-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition B")
                .resourceId("off-b")
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.b@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.getApprovalById(reqB.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("12. PC A approves another programme approval -> 403 Forbidden")
    void testPcA_ApprovesAnotherProgrammeApproval_Throws403() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-pc-b-2-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition B")
                .resourceId("off-b")
                .schoolId(schoolB.getId())
                .departmentId(deptB.getId())
                .masterProgrammeId(progB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.b@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqB.getId(), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // =========================================================================
    // 3. FACULTY RESTRICTIONS & SPOOFING TESTS
    // =========================================================================

    @Test
    @DisplayName("13. Faculty attempts approval -> 403 Forbidden")
    void testFaculty_AttemptsApproval_Throws403() {
        setAuthenticatedUser(facultyA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-fac-a-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("another.faculty@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqA.getId(), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("14. Faculty attempts direct verification -> 403 Forbidden")
    void testFaculty_AttemptsDirectVerification_Throws403() {
        setAuthenticatedUser(facultyA);

        Map<String, String> body = Map.of(
                "key", offeringA.getId(),
                "statusType", "coStatus",
                "statusValue", "APPROVED"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.verifyStatus(body));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("15. Faculty attempts direct revision request -> 403 Forbidden")
    void testFaculty_AttemptsDirectRevisionRequest_Throws403() {
        setAuthenticatedUser(facultyA);

        Map<String, String> body = Map.of(
                "key", offeringA.getId(),
                "statusType", "coStatus",
                "statusValue", "REVISION_REQUESTED",
                "remarks", "Revise COs"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.requestRevisionDirect(body));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("16. Spoofed actorName in approve request body is ignored (authenticated user name saved)")
    void testSpoofedActorName_IsIgnored() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-spoof-1-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.a@dypiu.ac.in")
                .build());

        Map<String, String> body = Map.of(
                "actorName", "Dr. Fake Director",
                "actorRole", "DIRECTOR"
        );

        ResponseEntity<ApiResponse<com.dypiu.nba.dto.ApprovalActionResultDto>> res = approvalController.approveRequest(reqA.getId(), body);
        assertNotNull(res.getBody());
        com.dypiu.nba.dto.ApprovalActionResultDto approved = res.getBody().getData();

        // Must record PC Alpha (authenticated user's name), NOT "Dr. Fake Director"
        assertEquals(pcA.getName(), approved.getReviewedBy().get("name"));

        List<ApprovalHistory> history = approvalHistoryRepository.findByApprovalRequestId(reqA.getId());
        assertFalse(history.isEmpty());
        ApprovalHistory hist = history.get(0);
        assertEquals(pcA.getName(), hist.getActorName());
        assertEquals("PROGRAMME_COORDINATOR", hist.getActorRole());
        assertEquals(pcA.getId(), hist.getActorId());
    }

    @Test
    @DisplayName("17. Spoofed actorRole in reject request body is ignored")
    void testSpoofedActorRole_IsIgnored() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-spoof-2-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.a@dypiu.ac.in")
                .build());

        Map<String, String> body = Map.of(
                "actorName", "Anonymous Actor",
                "actorRole", "ATTEMPTED_SPOOF_ROLE",
                "remarks", "Needs changes"
        );

        ResponseEntity<ApiResponse<com.dypiu.nba.dto.ApprovalActionResultDto>> res = approvalController.rejectRequest(reqA.getId(), body);
        assertNotNull(res.getBody());
        com.dypiu.nba.dto.ApprovalActionResultDto rejected = res.getBody().getData();
        assertEquals("REVISION_REQUESTED", rejected.getStatus());
        assertEquals(pcA.getName(), rejected.getReviewedBy().get("name"));

        List<ApprovalHistory> history = approvalHistoryRepository.findByApprovalRequestId(reqA.getId());
        assertFalse(history.isEmpty());
        assertEquals("PROGRAMME_COORDINATOR", history.get(0).getActorRole());
    }

    // =========================================================================
    // 4. TRANSITIONS & SELF-APPROVAL
    // =========================================================================

    @Test
    @DisplayName("18. Self-approval is blocked -> 403 Forbidden")
    void testSelfApproval_IsBlocked() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-self-1-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy(pcA.getEmail())
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqA.getId(), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("19. Double approval -> Safe idempotent no-op")
    void testDoubleApproval_IsIdempotent() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-double-1-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("faculty.a@dypiu.ac.in")
                .build());

        // First approval
        ResponseEntity<ApiResponse<com.dypiu.nba.dto.ApprovalActionResultDto>> res1 = approvalController.approveRequest(reqA.getId(), null);
        assertEquals("APPROVED", res1.getBody().getData().getStatus());

        // Second approval (idempotent no-op)
        ResponseEntity<ApiResponse<com.dypiu.nba.dto.ApprovalActionResultDto>> res2 = approvalController.approveRequest(reqA.getId(), null);
        assertEquals("APPROVED", res2.getBody().getData().getStatus());

        // Only 1 history record should be generated for the actual approval
        List<ApprovalHistory> history = approvalHistoryRepository.findByApprovalRequestId(reqA.getId());
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("20. Approving an already REJECTED request directly -> 409 Conflict")
    void testApproveRejectedDirectly_Throws409() {
        setAuthenticatedUser(pcA);

        ApprovalRequest reqA = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-conf-1-" + System.nanoTime())
                .type(ApprovalType.CO_DEFINITION)
                .title("CO Definition A")
                .resourceId(offeringA.getId())
                .schoolId(schoolA.getId())
                .departmentId(deptA.getId())
                .masterProgrammeId(progA.getId())
                .programmeBatchCourseId(offeringA.getId())
                .status(ApprovalStatus.REJECTED)
                .submittedBy("faculty.a@dypiu.ac.in")
                .build());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.approveRequest(reqA.getId(), null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("21. HOD A verifies valid course allocation -> Success")
    void testHodA_VerifiesCourseAllocation_Success() {
        setAuthenticatedUser(hodA);

        Map<String, String> body = Map.of(
                "key", "allocation-" + progA.getId(),
                "statusType", "allocationStatus",
                "statusValue", "APPROVED",
                "remarksValue", "Allocation verified"
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> res = approvalController.verifyStatus(body);
        assertNotNull(res.getBody());
        assertEquals("APPROVED", res.getBody().getData().get("allocationStatus"));
    }

    @Test
    @DisplayName("22. HOD A attempts to verify School B course allocation -> 403 Forbidden")
    void testHodA_VerifiesSchoolBAllocation_Throws403() {
        setAuthenticatedUser(hodA);

        Map<String, String> body = Map.of(
                "key", "allocation-" + progB.getId(),
                "statusType", "allocationStatus",
                "statusValue", "APPROVED"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.verifyStatus(body));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("23. Action dispatcher delegates correctly with scope validation")
    void testActionDispatcher_ValidatesScope() {
        setAuthenticatedUser(directorA);

        ApprovalRequest reqB = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-action-b-" + System.nanoTime())
                .type(ApprovalType.OUTCOME_FRAMEWORK)
                .title("Outcome Framework B")
                .resourceId("res-b")
                .schoolId(schoolB.getId())
                .status(ApprovalStatus.PENDING)
                .submittedBy("Sub-B")
                .build());

        Map<String, String> body = Map.of("action", "APPROVE");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                approvalController.actionRequest(reqB.getId(), body));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
