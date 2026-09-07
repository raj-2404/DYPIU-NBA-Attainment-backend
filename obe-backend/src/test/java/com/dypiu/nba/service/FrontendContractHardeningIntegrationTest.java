package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@WithMockUser(roles = "IQAC")
public class FrontendContractHardeningIntegrationTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AcademicService academicService;

    @Autowired
    private OutcomeService outcomeService;

    @Autowired
    private AttainmentCalculationService attainmentService;

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private com.dypiu.nba.repository.UserRepository userRepository;

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
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private AttainmentConfigurationRepository configRepository;

    @Autowired
    private CourseAtrRepository courseAtrRepository;

    private School testSchool;
    private Department testDept;
    private MasterProgramme testProg;
    private ProgrammeBatch testMasterProgrammeBatch;
    private ProgrammeBatchCourse testMasterCourse;
    private ProgrammeBatchCourse testOffering;

    @BeforeEach
    public void setup() {
        if (userRepository.findByUsername("user").isEmpty()) {
            userRepository.save(com.dypiu.nba.entity.User.builder()
                .username("user")
                .email("user@dypiu.ac.in")
                .name("Test User")
                .role(com.dypiu.nba.entity.UserRole.IQAC)
                .passwordHash("dummy")
                .build());
        }

        testSchool = schoolRepository.save(School.builder()
                .id("sch-test-" + UUID.randomUUID().toString().substring(0, 6))
                .code("SOE-TEST")
                .name("School of Engineering Test")
                .directorName("Dr. Suresh Patil")
                .directorEmail("director.test@dypiu.ac.in")
                .build());

        testDept = departmentRepository.save(Department.builder()
                .id("dept-test-" + UUID.randomUUID().toString().substring(0, 6))
                .schoolId(testSchool.getId())
                .code("CSE-TEST")
                .name("Computer Science and Engineering")
                .hod("Dr. Ananya Joshi")
                .hodEmail("hod.cse.test@dypiu.ac.in")
                .build());

        testProg = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-test-" + UUID.randomUUID().toString().substring(0, 6))
                .departmentId(testDept.getId())
                .code("BTECH-CSE-TEST")
                .name("B.Tech Computer Science and Engineering")
                .coordinator("Dr. Rahul Verma")
                .coordinatorEmail("pc.cse.test@dypiu.ac.in")
                .build());

        testMasterProgrammeBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-test-" + UUID.randomUUID().toString().substring(0, 6))
                .masterProgrammeId(testProg.getId())
                .name("2022-2026")
                .startYear(2022)
                .endYear(2026)
                .status("ACTIVE")
                .build());

        testMasterCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(testMasterProgrammeBatch.getId()).semester(1)
                .id("crs-test-" + UUID.randomUUID().toString().substring(0, 6))
                .code("CS301-TEST")
                .name("Computer Networks Test")
                .masterProgrammeId(testProg.getId())
                .credits(4)
                .courseType("Theory")
                .status("ACTIVE")
                .build());

        testOffering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-test-" + UUID.randomUUID().toString().substring(0, 6))
                .masterCourseId(testMasterCourse.getId())
                .programmeBatchId(testMasterProgrammeBatch.getId())
                .semester(5)
                .courseCoordinatorName("Prof. John Doe")
                .status("ACTIVE")
                .build());
    }

    @Test
    public void testCaseA_CourseOfferingVerificationStatus() {
        // Uninitialized state
        Map<String, Object> initialStatus = approvalService.getVerificationStatus(testOffering.getId());
        assertNotNull(initialStatus);
        assertTrue(initialStatus.containsKey("configStatus"));
        assertTrue(initialStatus.containsKey("coStatus"));
        assertTrue(initialStatus.containsKey("atrStatus"));
        assertTrue(initialStatus.containsKey("configRemarks"));
        assertTrue(initialStatus.containsKey("coRemarks"));
        assertTrue(initialStatus.containsKey("atrRemarks"));
        assertTrue(initialStatus.containsKey("verifiedBy"));
        assertEquals("DRAFT", initialStatus.get("configStatus"));
        assertEquals("APPROVED", initialStatus.get("coStatus"));
        assertEquals("DRAFT", initialStatus.get("atrStatus"));

        // Verify MasterCourse Offering ATR
        Map<String, Object> verified = approvalService.verifyStatus(
                testOffering.getId(),
                "atrStatus",
                "APPROVED",
                "MasterCourse ATR verified.",
                "Test User"
        );
        assertEquals("APPROVED", verified.get("atrStatus"));
        assertEquals("MasterCourse ATR verified.", verified.get("atrRemarks"));
        assertEquals("Test User", verified.get("verifiedBy"));

        // Request Revision on COs
        Map<String, Object> revised = approvalService.requestRevisionStatus(
                testOffering.getId(),
                "coStatus",
                "REVISION_REQUESTED",
                "Revise CO2 Bloom taxonomy.",
                "Test User"
        );
        assertEquals("REVISION_REQUESTED", revised.get("coStatus"));
        assertEquals("Revise CO2 Bloom taxonomy.", revised.get("coRemarks"));
    }

    @Test
    public void testCaseB_AllocationVerificationStatus() {
        String allocKey = "allocation-" + testProg.getId();

        // 1. Initial query before any allocation
        Map<String, Object> initial = approvalService.getVerificationStatus(allocKey);
        assertNotNull(initial);
        assertEquals("DRAFT", initial.get("allocationStatus"));
        assertEquals("", initial.get("allocationRemarks"));
        assertEquals("", initial.get("verifiedBy"));

        // 2. Direct verify action by HOD
        Map<String, Object> verified = approvalService.verifyStatus(
                allocKey,
                "allocationStatus",
                "APPROVED",
                "All course allocations approved.",
                "Test User"
        );
        assertNotNull(verified);
        assertEquals("APPROVED", verified.get("allocationStatus"));
        assertEquals("All course allocations approved.", verified.get("allocationRemarks"));
        assertEquals("Test User", verified.get("verifiedBy"));

        // 3. Request revision action by HOD
        Map<String, Object> revised = approvalService.requestRevisionStatus(
                allocKey,
                "allocationStatus",
                "REVISION_REQUESTED",
                "Reassign CS302 coordinator.",
                "Test User"
        );
        assertNotNull(revised);
        assertEquals("REVISION_REQUESTED", revised.get("allocationStatus"));
        assertEquals("Reassign CS302 coordinator.", revised.get("allocationRemarks"));
    }

    @Test
    public void testCaseC_PoPsoTargetsVerificationStatus() {
        String targetsKey = "targets-" + testProg.getId();

        // 1. Initial query
        Map<String, Object> initial = approvalService.getVerificationStatus(targetsKey);
        assertNotNull(initial);
        assertEquals("DRAFT", initial.get("poPsoTargetsStatus"));
        assertEquals("", initial.get("poPsoTargetsRemarks"));
        assertEquals("", initial.get("verifiedBy"));

        // 2. Direct verify action
        Map<String, Object> verified = approvalService.verifyStatus(
                targetsKey,
                "poPsoTargetsStatus",
                "APPROVED",
                "Targets approved at 2.50 threshold.",
                "Test User"
        );
        assertNotNull(verified);
        assertEquals("APPROVED", verified.get("poPsoTargetsStatus"));
        assertEquals("Targets approved at 2.50 threshold.", verified.get("poPsoTargetsRemarks"));
        assertEquals("Test User", verified.get("verifiedBy"));

        // 3. Request revision
        Map<String, Object> revised = approvalService.requestRevisionStatus(
                targetsKey,
                "poPsoTargetsStatus",
                "REVISION_REQUESTED",
                "Adjust PSO2 target to 2.60.",
                "Test User"
        );
        assertNotNull(revised);
        assertEquals("REVISION_REQUESTED", revised.get("poPsoTargetsStatus"));
        assertEquals("Adjust PSO2 target to 2.60.", revised.get("poPsoTargetsRemarks"));
    }

    @Test
    public void testCaseD_ProgrammeAtrVerificationStatus() {
        String progAtrKey = "prog-atr-" + testProg.getId();

        // 1. Initial query
        Map<String, Object> initial = approvalService.getVerificationStatus(progAtrKey);
        assertNotNull(initial);
        assertEquals("DRAFT", initial.get("programmeAtrStatus"));
        assertEquals("", initial.get("programmeAtrRemarks"));
        assertEquals("", initial.get("verifiedBy"));

        // 2. Direct verify action
        Map<String, Object> verified = approvalService.verifyStatus(
                progAtrKey,
                "programmeAtrStatus",
                "APPROVED",
                "MasterProgramme ATR verified with HoD remarks.",
                "Test User"
        );
        assertNotNull(verified);
        assertEquals("APPROVED", verified.get("programmeAtrStatus"));
        assertEquals("MasterProgramme ATR verified with HoD remarks.", verified.get("programmeAtrRemarks"));
        assertEquals("Test User", verified.get("verifiedBy"));

        // 3. Request revision action
        Map<String, Object> revised = approvalService.requestRevisionStatus(
                progAtrKey,
                "programmeAtrStatus",
                "REVISION_REQUESTED",
                "Please elaborate on action taken for PO4.",
                "Test User"
        );
        assertNotNull(revised);
        assertEquals("REVISION_REQUESTED", revised.get("programmeAtrStatus"));
        assertEquals("Please elaborate on action taken for PO4.", revised.get("programmeAtrRemarks"));
    }

    @Test
    public void testUnknownAndEmptyKeys() {
        Map<String, Object> emptyRes = approvalService.getVerificationStatus("");
        assertNotNull(emptyRes);
        assertTrue(emptyRes.isEmpty());

        Map<String, Object> nullRes = approvalService.getVerificationStatus(null);
        assertNotNull(nullRes);
        assertTrue(nullRes.isEmpty());

        Map<String, Object> unknownRes = approvalService.getVerificationStatus("unknown-entity-12345");
        assertNotNull(unknownRes);
        assertTrue(unknownRes.containsKey("configStatus"));
        assertEquals("DRAFT", unknownRes.get("configStatus"));
    }

    @Test
    public void testMasterCourseAllocationAndApprovalWorkflow() {
        List<Map<String, Object>> allocations = List.of(
                Map.of(
                        "masterCourseId", testMasterCourse.getId(),
                        "coordinatorEmail", "faculty.test@dypiu.ac.in",
                        "courseCoordinatorName", "Prof. Jane Smith"
                )
        );

        Map<String, Object> result = academicService.allocateCourses(testProg.getId(), null, allocations);
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));

        // Verify that course coordinator name was updated
        ProgrammeBatchCourse updatedMasterCourse = programmeBatchCourseRepository.findById(testMasterCourse.getId()).orElse(null);
        assertNotNull(updatedMasterCourse);
        assertEquals("Prof. Jane Smith", updatedMasterCourse.getCoordinator());

        // Verify that an ApprovalRequest of type COURSE_ALLOCATION was generated
        List<ApprovalRequest> reqs = approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.COURSE_ALLOCATION && testProg.getId().equals(a.getMasterProgrammeId()))
                .toList();
        assertFalse(reqs.isEmpty());
        assertEquals(ApprovalStatus.PENDING, reqs.get(0).getStatus());
    }

    @Test
    public void testCoAttainmentDetailsAndSummaryCalculation() {
        // Create 3 course outcomes
        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-t1-" + UUID.randomUUID().toString().substring(0, 6))
                .programmeBatchCourseId(testOffering.getId())
                .code("CO1")
                .statement("Analyze algorithms")
                .targetLevel(new BigDecimal("2.50"))
                .bloomsLevel("L4 - Analyze")
                .build());

        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-t2-" + UUID.randomUUID().toString().substring(0, 6))
                .programmeBatchCourseId(testOffering.getId())
                .code("CO2")
                .statement("Apply data structures")
                .targetLevel(new BigDecimal("2.50"))
                .bloomsLevel("L3 - Apply")
                .build());

        Map<String, Object> coAttainment = attainmentService.calculateCourseCoAttainment(testOffering.getId());
        assertNotNull(coAttainment);
        assertNotNull(coAttainment.get("directAttainment"));
        assertNotNull(coAttainment.get("indirectAttainment"));
        assertNotNull(coAttainment.get("overallCoAttainment"));
        assertNotNull(coAttainment.get("overallCOAttainment"));

        List<Map<String, Object>> coDetails = (List<Map<String, Object>>) coAttainment.get("coDetails");
        assertNotNull(coDetails);
        assertEquals(2, coDetails.size());

        Map<String, Object> firstCo = coDetails.get(0);
        assertTrue(firstCo.containsKey("coCode"));
        assertTrue(firstCo.containsKey("directScore"));
        assertTrue(firstCo.containsKey("indirectScore"));
        assertTrue(firstCo.containsKey("finalAttainment"));
        assertTrue(firstCo.containsKey("target"));
        assertTrue(firstCo.containsKey("targetMet"));
    }

    @Test
    public void testMasterCourseMappingMatrixCalculation() {
        CourseOutcome co1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-map1-" + UUID.randomUUID().toString().substring(0, 6))
                .programmeBatchCourseId(testOffering.getId())
                .code("CO1")
                .statement("Test CO1")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        CourseMappingMatrixDto matrixDto = outcomeService.getCourseMappings(testOffering.getId());
        assertNotNull(matrixDto);
        assertNotNull(matrixDto.getMatrix());
        assertNotNull(matrixDto.getPoAverages());
        assertNotNull(matrixDto.getPsoAverages());
        assertTrue(matrixDto.getMatrix().containsKey("CO1"));
    }

    @Test
    public void testRoleScopedDashboardAndHodCoordinators() {
        List<Map<String, Object>> coords = academicService.getHodCoordinators(testDept.getId());
        assertNotNull(coords);
        assertFalse(coords.isEmpty());
        assertEquals("Dr. Rahul Verma", coords.get(0).get("coordinatorName"));

        Map<String, Object> assignPayload = Map.of(
                "masterProgrammeId", testProg.getId(),
                "coordinatorName", "Dr. New Coordinator",
                "coordinatorEmail", "new.pc@dypiu.ac.in"
        );
        Map<String, Object> assignRes = academicService.assignHodCoordinator(assignPayload);
        assertNotNull(assignRes);
        assertTrue((Boolean) assignRes.get("success"));

        MasterProgramme updatedProg = masterProgrammeRepository.findById(testProg.getId()).orElse(null);
        assertNotNull(updatedProg);
        assertEquals("Dr. New Coordinator", updatedProg.getCoordinator());
    }

    @Test
    public void testConsolidatedOutcomesAndCoTargets() {
        Map<String, Object> outcomes = academicService.getConsolidatedOutcomes(testProg.getId(), testMasterProgrammeBatch.getId());
        assertNotNull(outcomes);
        assertTrue(outcomes.containsKey("pos"));
        assertTrue(outcomes.containsKey("psos"));
        assertTrue(outcomes.containsKey("peos"));

        Map<String, Object> coTargets = academicService.getCourseCoTargets(testMasterCourse.getId(), testMasterProgrammeBatch.getId());
        assertNotNull(coTargets);
        assertTrue(coTargets.containsKey("coTargets"));

        Map<String, Object> updateTargets = Map.of("CO1", new BigDecimal("2.80"), "CO2", new BigDecimal("2.60"));
        Map<String, Object> savedTargetsRes = academicService.saveCourseCoTargets(testMasterCourse.getId(), updateTargets);
        assertNotNull(savedTargetsRes);
        assertTrue((Boolean) savedTargetsRes.get("success"));
    }

    @Test
    public void testGetApprovalsAndActionEndpoint() {
        // Submit approval request
        ApprovalRequest req = ApprovalRequest.builder()
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Allocations for " + testProg.getName())
                .resourceId("allocation-" + testProg.getId())
                .masterProgrammeId(testProg.getId())
                .schoolId(testSchool.getId())
                .submittedBy("Test PC")
                .build();
        ApprovalRequest submitted = approvalService.submitApprovalRequest(req);
        assertNotNull(submitted.getId());
        assertEquals(ApprovalStatus.PENDING, submitted.getStatus());

        // Test GET /approvals with no params
        List<ApprovalRequest> allApprovals = approvalService.getApprovals(null, null, null, null, null);
        assertNotNull(allApprovals);
        assertFalse(allApprovals.isEmpty());

        // Test GET /approvals filtered by status
        List<ApprovalRequest> pendingApprovals = approvalService.getApprovals(null, "PENDING", null, null, null);
        assertNotNull(pendingApprovals);
        assertTrue(pendingApprovals.stream().anyMatch(a -> a.getId().equals(submitted.getId())));

        // Test action: APPROVE
        ApprovalRequest approved = approvalService.actionRequest(submitted.getId(), "APPROVE", "Approved by HOD", "Test HOD", "HOD");
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        assertEquals("Test User", approved.getApprovedBy());

        // Test action: REJECT / REQUEST_REVISION
        ApprovalRequest revised = approvalService.actionRequest(submitted.getId(), "REQUEST_REVISION", "Please revise allocations", "Test HOD", "HOD");
        assertEquals(ApprovalStatus.REVISION_REQUESTED, revised.getStatus());
        assertEquals("Please revise allocations", revised.getRemarks());
    }
}
