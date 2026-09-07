package com.dypiu.nba.security;

import com.dypiu.nba.controller.AttainmentController;
import com.dypiu.nba.controller.DashboardController;
import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.AttainmentCalculationService;
import com.dypiu.nba.service.MappingService;
import com.dypiu.nba.service.OutcomeService;
import com.dypiu.nba.service.ReportAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class CourseCoordinatorScopeSecurityTest {

    @Autowired
    private AcademicService academicService;

    @Autowired
    private OutcomeService outcomeService;

    @Autowired
    private AtrService atrService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private AttainmentCalculationService attainmentCalculationService;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private AttainmentController attainmentController;

    @Autowired
    private ReportAccessService reportAccessService;

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
    private CourseOutcomeRepository courseOutcomeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttainmentConfigurationRepository configRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private School schoolA;
    private School schoolB;
    private Department deptA1;
    private Department deptA2;
    private Department deptB1;
    private MasterProgramme progA1;
    private MasterProgramme progA2;
    private MasterProgramme progB1;
    private ProgrammeBatch batchA1;
    private ProgrammeBatch batchA2;
    private ProgrammeBatch batchB1;
    private ProgrammeBatchCourse courseA1;
    private ProgrammeBatchCourse courseA2;
    private ProgrammeBatchCourse courseB1;

    private ProgrammeBatchCourse offeringA1;
    private ProgrammeBatchCourse offeringA2;
    private ProgrammeBatchCourse offeringB1;

    private CourseOutcome coA1;
    private CourseOutcome coA2;
    private CourseOutcome coB1;

    private User ccUserA1;
    private User facultyUserA1;
    private User ccUserB1;
    private User unassignedFaculty;
    private User iqacUser;
    private User directorUserA;
    private User hodUserA1;
    private User pcUserA1;

    @BeforeEach
    void setUpTestData() {
        SecurityContextHolder.clearContext();

        // 1. Schools
        schoolA = schoolRepository.save(School.builder()
                .id("sch-cc-a-" + System.nanoTime())
                .name("School of Engineering A")
                .code("SOE-A")
                .directorName("Director A")
                .directorEmail("director.a@dypiu.ac.in")
                .build());

        schoolB = schoolRepository.save(School.builder()
                .id("sch-cc-b-" + System.nanoTime())
                .name("School of Management B")
                .code("SOM-B")
                .directorName("Director B")
                .directorEmail("director.b@dypiu.ac.in")
                .build());

        // 2. Departments
        deptA1 = departmentRepository.save(Department.builder()
                .id("dept-cc-a1-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Computer Engineering")
                .code("CE")
                .hod("HOD A1")
                .hodEmail("hod.a1@dypiu.ac.in")
                .build());

        deptA2 = departmentRepository.save(Department.builder()
                .id("dept-cc-a2-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Mechanical Engineering")
                .code("ME")
                .hod("HOD A2")
                .hodEmail("hod.a2@dypiu.ac.in")
                .build());

        deptB1 = departmentRepository.save(Department.builder()
                .id("dept-cc-b1-" + System.nanoTime())
                .schoolId(schoolB.getId())
                .name("Management Studies")
                .code("MS")
                .hod("HOD B1")
                .hodEmail("hod.b1@dypiu.ac.in")
                .build());

        // 3. Programmes
        progA1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-cc-a1-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech Computer Science")
                .code("BT-CS")
                .build());

        progA2 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-cc-a2-" + System.nanoTime())
                .departmentId(deptA2.getId())
                .name("B.Tech Mechanical")
                .code("BT-ME")
                .build());

        progB1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-cc-b1-" + System.nanoTime())
                .departmentId(deptB1.getId())
                .name("MBA")
                .code("MBA")
                .build());

        // 4. Batches
        batchA1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-cc-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("ProgrammeBatch CS 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .build());

        batchA2 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-cc-a2-" + System.nanoTime())
                .masterProgrammeId(progA2.getId())
                .name("ProgrammeBatch ME 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .build());

        batchB1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-cc-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("ProgrammeBatch MBA 2023-2025")
                .startYear(2023)
                .endYear(2025)
                .durationYears(2)
                .build());

        // 5. Courses
        courseA1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA1.getId()).semester(1)
                .id("crs-cc-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("Data Structures")
                .code("CS201")
                .credits(4)
                .courseType("CORE")
                .build());

        courseA2 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA2.getId()).semester(1)
                .id("crs-cc-a2-" + System.nanoTime())
                .masterProgrammeId(progA2.getId())
                .name("Thermodynamics")
                .code("ME201")
                .credits(4)
                .courseType("CORE")
                .build());

        courseB1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchB1.getId()).semester(1)
                .id("crs-cc-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("Financial Management")
                .code("MBA101")
                .credits(3)
                .courseType("CORE")
                .build());

        // 6. Users
        ccUserA1 = userRepository.save(User.builder()
                .username("cc_alice_" + System.nanoTime())
                .email("alice.cc." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Prof. Alice CC")
                .role(UserRole.FACULTY)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .masterProgrammeId(progA1.getId())
                .isActive(true)
                .build());

        facultyUserA1 = userRepository.save(User.builder()
                .username("faculty_bob_" + System.nanoTime())
                .email("bob.faculty." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Prof. Bob Faculty")
                .role(UserRole.FACULTY)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .masterProgrammeId(progA1.getId())
                .isActive(true)
                .build());

        ccUserB1 = userRepository.save(User.builder()
                .username("cc_charlie_" + System.nanoTime())
                .email("charlie.cc." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Prof. Charlie CC")
                .role(UserRole.FACULTY)
                .schoolId(schoolB.getId())
                .departmentId(deptB1.getId())
                .masterProgrammeId(progB1.getId())
                .isActive(true)
                .build());

        unassignedFaculty = userRepository.save(User.builder()
                .username("faculty_david_" + System.nanoTime())
                .email("david.unassigned." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Prof. David Unassigned")
                .role(UserRole.FACULTY)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .masterProgrammeId(progA1.getId())
                .isActive(true)
                .build());

        iqacUser = userRepository.save(User.builder()
                .username("iqac_user_" + System.nanoTime())
                .email("iqac." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("IQAC Officer")
                .role(UserRole.IQAC)
                .isActive(true)
                .build());

        directorUserA = userRepository.save(User.builder()
                .username("director_a_" + System.nanoTime())
                .email("director.a." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Director School A")
                .role(UserRole.DIRECTOR)
                .schoolId(schoolA.getId())
                .isActive(true)
                .build());

        hodUserA1 = userRepository.save(User.builder()
                .username("hod_a1_" + System.nanoTime())
                .email("hod.a1." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("HOD Dept A1")
                .role(UserRole.HOD)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .isActive(true)
                .build());

        pcUserA1 = userRepository.save(User.builder()
                .username("pc_a1_" + System.nanoTime())
                .email("pc.a1." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("PC Prog A1")
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .masterProgrammeId(progA1.getId())
                .isActive(true)
                .build());

        User otherCoordinatorUser = userRepository.save(User.builder()
                .username("other_coord_" + System.nanoTime())
                .email("other.coord." + System.nanoTime() + "@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Other Coordinator")
                .role(UserRole.FACULTY)
                .schoolId(schoolA.getId())
                .departmentId(deptA2.getId())
                .masterProgrammeId(progA2.getId())
                .isActive(true)
                .build());

        // 7. MasterCourse Offerings
        offeringA1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-cc-a1-" + System.nanoTime())
                .masterCourseId(courseA1.getId())
                .programmeBatchId(batchA1.getId())
                .semester(3)
                .courseCoordinatorId(ccUserA1.getId())
                .courseCoordinatorName(ccUserA1.getName())
                .assignedFaculty(ccUserA1.getName() + " (" + ccUserA1.getEmail() + "), " + facultyUserA1.getName() + " (" + facultyUserA1.getEmail() + ")")
                .build());

        offeringA2 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-cc-a2-" + System.nanoTime())
                .masterCourseId(courseA2.getId())
                .programmeBatchId(batchA2.getId())
                .semester(3)
                .courseCoordinatorId(otherCoordinatorUser.getId())
                .courseCoordinatorName(otherCoordinatorUser.getName())
                .assignedFaculty(otherCoordinatorUser.getName() + " (" + otherCoordinatorUser.getEmail() + ")")
                .build());

        offeringB1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-cc-b1-" + System.nanoTime())
                .masterCourseId(courseB1.getId())
                .programmeBatchId(batchB1.getId())
                .semester(1)
                .courseCoordinatorId(ccUserB1.getId())
                .courseCoordinatorName(ccUserB1.getName())
                .assignedFaculty(ccUserB1.getName() + " (" + ccUserB1.getEmail() + ")")
                .build());

        // 8. MasterCourse Outcomes
        coA1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-cc-a1-" + System.nanoTime())
                .programmeBatchCourseId(offeringA1.getId())
                .code("CO1")
                .statement("Apply data structures algorithms")
                .targetLevel(new BigDecimal("2.50"))
                .bloomsLevel("L3 - Apply")
                .build());

        coA2 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-cc-a2-" + System.nanoTime())
                .programmeBatchCourseId(offeringA2.getId())
                .code("CO1")
                .statement("Analyze heat transfer mechanisms")
                .targetLevel(new BigDecimal("2.50"))
                .bloomsLevel("L4 - Analyze")
                .build());

        coB1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-cc-b1-" + System.nanoTime())
                .programmeBatchCourseId(offeringB1.getId())
                .code("CO1")
                .statement("Evaluate corporate balance sheet")
                .targetLevel(new BigDecimal("2.50"))
                .bloomsLevel("L5 - Evaluate")
                .build());

        // 9. Attainment Config
        configRepository.save(AttainmentConfiguration.builder()
                .id("cfg-" + offeringA1.getId())
                .programmeBatchCourseId(offeringA1.getId())
                .directWeight(new BigDecimal("80.00"))
                .indirectWeight(new BigDecimal("20.00"))
                .directThreshold(new BigDecimal("60.00"))
                .indirectThreshold(new BigDecimal("60.00"))
                .status(AttainmentConfigStatus.DRAFT)
                .build());

        // 10. Approve Course Allocations for test programmes
        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("appr-alloc-" + progA1.getId())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Course Allocation for " + progA1.getId())
                .resourceId("allocation-" + progA1.getId())
                .masterProgrammeId(progA1.getId())
                .status(ApprovalStatus.APPROVED)
                .submittedBy("pc.a1@dypiu.ac.in")
                .approvedBy("hod.a1@dypiu.ac.in")
                .build());

        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("appr-alloc-" + progA2.getId())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Course Allocation for " + progA2.getId())
                .resourceId("allocation-" + progA2.getId())
                .masterProgrammeId(progA2.getId())
                .status(ApprovalStatus.APPROVED)
                .submittedBy("pc.a2@dypiu.ac.in")
                .approvedBy("hod.a2@dypiu.ac.in")
                .build());

        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("appr-alloc-" + progB1.getId())
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Course Allocation for " + progB1.getId())
                .resourceId("allocation-" + progB1.getId())
                .masterProgrammeId(progB1.getId())
                .status(ApprovalStatus.APPROVED)
                .submittedBy("pc.b1@dypiu.ac.in")
                .approvedBy("hod.b1@dypiu.ac.in")
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    private Principal createPrincipal(User user) {
        return () -> user.getEmail();
    }

    // =========================================================================
    // TEST CASES 1-30: COURSE COORDINATOR & FACULTY SCOPE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Scenario 1: MasterCourse Coordinator accesses own offering -> 200 OK")
    void testScenario1_CCAccessesOwnOffering() {
        authenticateUser(ccUserA1);
        assertDoesNotThrow(() -> reportAccessService.validateCourseOfferingAccess(ccUserA1, offeringA1.getId()));
    }

    @Test
    @DisplayName("Scenario 2: MasterCourse Coordinator accesses another offering -> 403 Forbidden")
    void testScenario2_CCAccessesAnotherOffering() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(ccUserA1, offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 3: MasterCourse Coordinator accesses another course -> 403 Forbidden")
    void testScenario3_CCAccessesAnotherCourse() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseAccess(ccUserA1, courseA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 4: MasterCourse Coordinator accesses another programme offering -> 403 Forbidden")
    void testScenario4_CCAccessesAnotherProgrammeOffering() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(ccUserA1, offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 5: MasterCourse Coordinator accesses another school offering -> 403 Forbidden")
    void testScenario5_CCAccessesAnotherSchoolOffering() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(ccUserA1, offeringB1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 6: Teaching Faculty accesses assigned offering -> 403 Forbidden")
    void testScenario6_FacultyAccessesAssignedOffering() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(facultyUserA1, offeringA1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 7: Teaching Faculty accesses unassigned offering -> 403 Forbidden")
    void testScenario7_FacultyAccessesUnassignedOffering() {
        authenticateUser(unassignedFaculty);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(unassignedFaculty, offeringA1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 8: Teaching Faculty accesses another faculty member's offering -> 403 Forbidden")
    void testScenario8_FacultyAccessesOtherFacultyOffering() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(facultyUserA1, offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 9: Teaching Faculty accesses unassigned course -> 403 Forbidden")
    void testScenario9_FacultyAccessesUnassignedCourse() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseAccess(facultyUserA1, courseA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 10: Teaching Faculty accesses unassigned programme batch -> 403 Forbidden")
    void testScenario10_FacultyAccessesUnassignedProgrammeBatch() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> academicService.getBatchById(batchB1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 11: Teaching Faculty accesses unassigned department course -> 403 Forbidden")
    void testScenario11_FacultyAccessesUnassignedDepartmentCourse() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> academicService.getCourseById(courseA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 12: Teaching Faculty accesses unassigned school course -> 403 Forbidden")
    void testScenario12_FacultyAccessesUnassignedSchoolCourse() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> academicService.getCourseById(courseB1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 13: Foreign courseId in CC dashboard -> 403 Forbidden")
    void testScenario13_ForeignCourseIdInDashboard() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dashboardController.getCourseCoordinatorDashboard(courseA2.getId(), null, null, principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 14: Foreign courseOfferingId in setup progress -> 403 Forbidden")
    void testScenario14_ForeignOfferingIdInSetupProgress() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> academicService.getCourseCoordinatorSetupProgress(ccUserA1.getEmail(), offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 15: Foreign courseOfferingId in setup progress update -> 403 Forbidden")
    void testScenario15_ForeignOfferingIdInSetupProgressUpdate() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> academicService.updateCourseCoordinatorSetupProgress(ccUserA1.getEmail(), offeringA2.getId(), 2));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 16: Foreign courseId in attainment config -> 403 Forbidden")
    void testScenario16_ForeignCourseIdInAttainmentConfig() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attainmentController.getConfig(courseA2.getId(), null, principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 17: Foreign courseId in attainment calculation -> 403 Forbidden")
    void testScenario17_ForeignCourseIdInAttainmentCalculation() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attainmentController.getCourseCoAttainment(courseA2.getId(), null, principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 18: Foreign courseOfferingId in direct assessment upload -> 403 Forbidden")
    void testScenario18_ForeignOfferingIdInDirectUpload() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        MockMultipartFile file = new MockMultipartFile("file", "marks.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attainmentController.uploadAssessmentDirect(file, null, offeringA2.getId(), null, "DIRECT", "INTERNAL", principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 19: Foreign courseOfferingId in indirect assessment upload -> 403 Forbidden")
    void testScenario19_ForeignOfferingIdInIndirectUpload() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        MockMultipartFile file = new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attainmentController.uploadAssessmentIndirect(file, null, offeringA2.getId(), null, principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 20: Foreign CO ID in mapping update -> 403 Forbidden")
    void testScenario20_ForeignCoIdInMappingUpdate() {
        authenticateUser(ccUserA1);
        List<CoPoMapping> mappings = List.of(CoPoMapping.builder().poCode("PO1").mappingLevel(3).build());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mappingService.saveCoPoMappings(coA2.getId(), mappings));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 21: Foreign courseId in attainment export -> 403 Forbidden")
    void testScenario21_ForeignCourseIdInExport() {
        authenticateUser(ccUserA1);
        Principal principal = createPrincipal(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attainmentController.exportAttainmentExcel(courseA2.getId(), null, principal));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 22: Foreign courseOfferingId in MasterCourse ATR -> 403 Forbidden")
    void testScenario22_ForeignOfferingIdInCourseAtr() {
        authenticateUser(ccUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> atrService.getCourseAtrReport(offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 23: Teaching Faculty cannot submit MasterCourse ATR unless they are MasterCourse Coordinator -> 403 Forbidden")
    void testScenario23_TeachingFacultyCannotSubmitCourseAtr() {
        authenticateUser(facultyUserA1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> atrService.submitCourseAtr(offeringA1.getId(), facultyUserA1.getName()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 24: MasterCourse Coordinator can complete authorized setup -> Success")
    void testScenario24_CCCanCompleteAuthorizedSetup() {
        authenticateUser(ccUserA1);
        CourseCoordinatorSetupProgressDto progress = academicService.completeCourseCoordinatorSetup(ccUserA1.getEmail(), offeringA1.getId());
        assertNotNull(progress);
        assertFalse(progress.getCompletedSteps().isEmpty());
        assertTrue(progress.getPendingSteps().isEmpty());
    }

    @Test
    @DisplayName("Scenario 25: MasterCourse Coordinator can access authorized ATR -> Success")
    void testScenario25_CCCanAccessAuthorizedAtr() {
        authenticateUser(ccUserA1);
        CourseAtrReportDto atr = atrService.getCourseAtrReport(offeringA1.getId());
        assertNotNull(atr);
        assertEquals(offeringA1.getId(), atr.getCourseOffering().getId());
    }

    @Test
    @DisplayName("Scenario 27: IQAC behavior (unrestricted access) -> Success")
    void testScenario27_IqacUnrestrictedAccess() {
        authenticateUser(iqacUser);
        Principal principal = createPrincipal(iqacUser);
        assertDoesNotThrow(() -> {
            reportAccessService.validateCourseOfferingAccess(iqacUser, offeringA1.getId());
            reportAccessService.validateCourseOfferingAccess(iqacUser, offeringB1.getId());
            attainmentController.getConfig(courseB1.getId(), null, principal);
        });
    }

    @Test
    @DisplayName("Scenario 28: DIRECTOR behavior unchanged (school-scoped)")
    void testScenario28_DirectorSchoolScoped() {
        authenticateUser(directorUserA);
        Principal principal = createPrincipal(directorUserA);
        assertDoesNotThrow(() -> {
            reportAccessService.validateCourseOfferingAccess(directorUserA, offeringA1.getId());
            reportAccessService.validateCourseOfferingAccess(directorUserA, offeringA2.getId());
        });
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(directorUserA, offeringB1.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 29: HOD behavior unchanged (department-scoped)")
    void testScenario29_HodDepartmentScoped() {
        authenticateUser(hodUserA1);
        assertDoesNotThrow(() -> {
            reportAccessService.validateCourseOfferingAccess(hodUserA1, offeringA1.getId());
        });
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportAccessService.validateCourseOfferingAccess(hodUserA1, offeringA2.getId()));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 31: Course Coordinator setup progress update with JSON body")
    void testScenario31_CourseCoordinatorSetupProgressWithBody() {
        authenticateUser(ccUserA1);
        Map<String, Object> body = Map.of(
                "masterCourseId", offeringA1.getId(),
                "stepNumber", 2,
                "completedSteps", List.of("1")
        );

        CourseCoordinatorSetupProgressDto dto = academicService.updateCourseCoordinatorSetupProgress(
                ccUserA1.getEmail(), null, null, body);

        assertNotNull(dto);
        assertEquals(2, dto.getCurrentStep());
        assertTrue(dto.getCompletedSteps().contains("1"));
        assertEquals(offeringA1.getId(), dto.getProgrammeBatchCourseId());
    }

    @Test
    @DisplayName("Scenario 32: Gatekeeper blocks Faculty/Course Coordinator when Course Allocation is unapproved")
    void testScenario32_UnapprovedCourseAllocation_FacultyBlocked() {
        // Create an unapproved offering under a new MasterProgramme
        MasterProgramme unapprovedProg = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-unapproved-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech AI")
                .code("BT-AI")
                .build());

        ProgrammeBatch unapprovedBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-unapproved-" + System.nanoTime())
                .masterProgrammeId(unapprovedProg.getId())
                .name("Batch AI 2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        ProgrammeBatchCourse unapprovedCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(unapprovedBatch.getId()).semester(1)
                .id("crs-unapproved-" + System.nanoTime())
                .masterProgrammeId(unapprovedProg.getId())
                .name("Artificial Intelligence")
                .code("AI101")
                .credits(3)
                .build());

        ProgrammeBatchCourse unapprovedOffering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-unapproved-" + System.nanoTime())
                .masterCourseId(unapprovedCourse.getId())
                .programmeBatchId(unapprovedBatch.getId())
                .semester(1)
                .courseCoordinatorId(ccUserA1.getId())
                .courseCoordinatorName(ccUserA1.getName())
                .assignedFaculty(ccUserA1.getName() + " (" + ccUserA1.getEmail() + ")")
                .build());

        // Authenticate as assigned faculty (ccUserA1)
        authenticateUser(ccUserA1);

        // 1. AcademicService offering fetch blocked
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                academicService.getProgrammeBatchCourseById(unapprovedOffering.getId()));
        assertEquals(403, ex1.getStatusCode().value());
        assertTrue(ex1.getReason().contains("Course allocation for this course has not been approved by the HOD yet"));

        // 2. ReportAccessService offering access blocked
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                reportAccessService.validateCourseOfferingAccess(ccUserA1, unapprovedOffering.getId()));
        assertEquals(403, ex2.getStatusCode().value());
        assertTrue(ex2.getReason().contains("Course allocation for this course has not been approved by the HOD yet"));

        // 3. OutcomeService CO fetch blocked
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () ->
                outcomeService.getCOsByCourse(unapprovedOffering.getId()));
        assertEquals(403, ex3.getStatusCode().value());

        // 4. MappingService CO mapping fetch blocked
        CourseOutcome dummyCo = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-dummy-" + System.nanoTime())
                .programmeBatchCourseId(unapprovedOffering.getId())
                .code("CO1")
                .statement("Dummy statement")
                .targetLevel(new BigDecimal("2.50"))
                .build());
        ResponseStatusException ex4 = assertThrows(ResponseStatusException.class, () ->
                mappingService.getCoPoMappings(dummyCo.getId()));
        assertEquals(403, ex4.getStatusCode().value());

        // 5. AtrService Course ATR fetch blocked
        ResponseStatusException ex5 = assertThrows(ResponseStatusException.class, () ->
                atrService.getCourseAtrReport(unapprovedOffering.getId()));
        assertEquals(403, ex5.getStatusCode().value());
    }

    @Test
    @DisplayName("Scenario 33: Management (HOD, PC, IQAC) can access unapproved Course Allocation")
    void testScenario33_UnapprovedCourseAllocation_ManagementAllowed() {
        MasterProgramme unapprovedProg = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-unapproved-mgmt-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech Robotics")
                .code("BT-ROB")
                .build());

        ProgrammeBatch unapprovedBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-unapproved-mgmt-" + System.nanoTime())
                .masterProgrammeId(unapprovedProg.getId())
                .name("Batch ROB 2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        ProgrammeBatchCourse unapprovedCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(unapprovedBatch.getId()).semester(1)
                .id("crs-unapproved-mgmt-" + System.nanoTime())
                .masterProgrammeId(unapprovedProg.getId())
                .name("Robotics 101")
                .code("ROB101")
                .credits(3)
                .build());

        ProgrammeBatchCourse unapprovedOffering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-unapproved-mgmt-" + System.nanoTime())
                .masterCourseId(unapprovedCourse.getId())
                .programmeBatchId(unapprovedBatch.getId())
                .semester(1)
                .courseCoordinatorId(ccUserA1.getId())
                .courseCoordinatorName(ccUserA1.getName())
                .assignedFaculty(ccUserA1.getName() + " (" + ccUserA1.getEmail() + ")")
                .build());

        // HOD can access
        authenticateUser(hodUserA1);
        assertDoesNotThrow(() -> academicService.getProgrammeBatchCourseById(unapprovedOffering.getId()));
        assertDoesNotThrow(() -> reportAccessService.validateCourseOfferingAccess(hodUserA1, unapprovedOffering.getId()));

        // IQAC can access
        authenticateUser(iqacUser);
        assertDoesNotThrow(() -> academicService.getProgrammeBatchCourseById(unapprovedOffering.getId()));
    }
}
