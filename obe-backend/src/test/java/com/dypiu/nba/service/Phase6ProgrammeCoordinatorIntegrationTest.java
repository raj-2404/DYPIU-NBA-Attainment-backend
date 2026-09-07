package com.dypiu.nba.service;

import com.dypiu.nba.dto.ProgrammeCoordinatorSetupProgressDto;
import com.dypiu.nba.dto.ProgrammeTargetDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "IQAC")
public class Phase6ProgrammeCoordinatorIntegrationTest {

    @Autowired
    private AcademicService academicService;

    @Autowired
    private OutcomeService outcomeService;

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
    private ProgrammeOutcomeRepository programmeOutcomeRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;

    @Autowired
    private PoCompetencyRepository poCompetencyRepository;

    @Autowired
    private PsoCompetencyRepository psoCompetencyRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private String progId1;
    private String progId2;
    private String programmeBatchId1;
    private String programmeBatchId2;
    private final String coordinatorEmail = "pc.test@dypiu.ac.in";

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("user").isEmpty()) {
            userRepository.save(com.dypiu.nba.entity.User.builder()
                .username("user")
                .email("user@dypiu.ac.in")
                .name("Test User")
                .role(com.dypiu.nba.entity.UserRole.IQAC)
                .passwordHash("dummy")
                .build());
        }

        School school = schoolRepository.save(School.builder()
                .id("sch-pc-test-" + UUID.randomUUID().toString().substring(0, 6))
                .name("School of Computing PC Test")
                .code("SOCT-" + UUID.randomUUID().toString().substring(0, 4))
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-pc-test-" + UUID.randomUUID().toString().substring(0, 6))
                .name("Computer Science Department PC Test")
                .code("CS-PCT")
                .schoolId(school.getId())
                .build());

        MasterProgramme prog1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pct-1-" + UUID.randomUUID().toString().substring(0, 6))
                .name("B.Tech Computer Science PCT 1")
                .code("BT-CS-1")
                .departmentId(dept.getId())
                .coordinator("Test PC")
                .coordinatorEmail(coordinatorEmail)
                .durationYears(4)
                .build());
        progId1 = prog1.getId();

        MasterProgramme prog2 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pct-2-" + UUID.randomUUID().toString().substring(0, 6))
                .name("B.Tech AI & Data Science PCT 2")
                .code("BT-AI-2")
                .departmentId(dept.getId())
                .coordinator("Test PC")
                .coordinatorEmail(coordinatorEmail)
                .durationYears(4)
                .build());
        progId2 = prog2.getId();

        ProgrammeBatch b1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pct-1-" + UUID.randomUUID().toString().substring(0, 6))
                .name("2022-2026 ProgrammeBatch A")
                .masterProgrammeId(progId1)
                .startYear(2022)
                .endYear(2026)
                .status("ACTIVE")
                .build());
        programmeBatchId1 = b1.getId();

        ProgrammeBatch b2 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pct-2-" + UUID.randomUUID().toString().substring(0, 6))
                .name("2023-2027 ProgrammeBatch B")
                .masterProgrammeId(progId1)
                .startYear(2023)
                .endYear(2027)
                .status("ACTIVE")
                .build());
        programmeBatchId2 = b2.getId();
    }

    @Test
    @DisplayName("TEST A: Initial Workflow State & ProgrammeBatch Isolation")
    void testInitialWorkflowAndBatchIsolation() {
        ProgrammeCoordinatorSetupProgressDto progressB1 = academicService.getProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1);
        assertNotNull(progressB1);
        assertEquals(progId1, progressB1.getMasterProgrammeId());
        assertEquals(programmeBatchId1, progressB1.getProgrammeBatchId());
        assertEquals(1, progressB1.getCurrentStep());
        assertTrue(progressB1.getCompletedSteps().isEmpty());

        // Update progress on ProgrammeBatch 1 (Step courses complete)
        academicService.updateProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1, 1, Map.of("completedSteps", List.of("courses")));

        // Verify ProgrammeBatch 1 progress updated
        ProgrammeCoordinatorSetupProgressDto updatedB1 = academicService.getProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1);
        assertTrue(updatedB1.getCompletedSteps().contains("courses"));

        // Verify ProgrammeBatch 2 progress remains unaffected (ProgrammeBatch Isolation)
        ProgrammeCoordinatorSetupProgressDto progressB2 = academicService.getProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId2);
        assertFalse(progressB2.getCompletedSteps().contains("courses"), "ProgrammeBatch 2 must not inherit ProgrammeBatch 1 completed steps");
    }

    @Test
    @DisplayName("TEST B: Non-Sequential Step Completion (courses -> indirect_attainment)")
    void testNonSequentialStepCompletion() {
        // Complete Step courses
        academicService.updateProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1, 1, Map.of("completedSteps", List.of("courses")));

        // Jump directly to Step indirect_attainment and complete it without completing po_pso_target
        academicService.updateProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1, 3, Map.of("completedSteps", List.of("courses", "indirect_attainment")));

        ProgrammeCoordinatorSetupProgressDto progress = academicService.getProgrammeCoordinatorSetupProgress(coordinatorEmail, progId1, programmeBatchId1);
        assertTrue(progress.getCompletedSteps().contains("courses"), "Step courses must be completed");
        assertFalse(progress.getCompletedSteps().contains("po_pso_target"), "Step po_pso_target must remain pending");
        assertTrue(progress.getCompletedSteps().contains("indirect_attainment"), "Step indirect_attainment must be completed");
        assertTrue(progress.getPendingSteps().contains("po_pso_target"), "Pending steps must explicitly contain Step po_pso_target");
    }

    @Test
    @DisplayName("TEST C: Step 1 ProgrammeBatchCourse Allocation - Save (Draft) vs Submit Boundary")
    void testStep1AllocationDraftVsSubmitBoundary() {
        ProgrammeBatchCourse c1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("crs-test-1-" + UUID.randomUUID().toString().substring(0, 6))
                .programmeBatchId(programmeBatchId1).semester(1).code("CS301")
                .name("Data Structures")
                .masterProgrammeId(progId1)
                .credits(3)
                .courseType("THEORY")
                .status("ACTIVE")
                .build());

        int initialApprovalCount = approvalRequestRepository.findByMasterProgrammeId(progId1).size();

        // 1. Save allocations as Draft (submit = false)
        List<Map<String, Object>> allocations = List.of(Map.of(
                "masterCourseId", c1.getId(),
                "coordinator", "Prof. Alice",
                "coordinatorEmail", "alice@dypiu.ac.in"
        ));
        Map<String, Object> draftRes = academicService.allocateCourses(progId1, null, allocations, false);
        assertTrue((Boolean) draftRes.get("success"));

        // Verify NO approval request was created
        List<ApprovalRequest> afterDraftApprovals = approvalRequestRepository.findByMasterProgrammeId(progId1);
        assertEquals(initialApprovalCount, afterDraftApprovals.size(), "Draft save must NOT create ApprovalRequest");

        // 2. Explicit Submit (submit = true)
        Map<String, Object> submitRes = academicService.allocateCourses(progId1, null, allocations, true);
        assertTrue((Boolean) submitRes.get("success"));

        List<ApprovalRequest> afterSubmitApprovals = approvalRequestRepository.findByMasterProgrammeId(progId1);
        assertEquals(initialApprovalCount + 1, afterSubmitApprovals.size(), "Explicit submit must create ApprovalRequest");
        ApprovalRequest req = afterSubmitApprovals.get(afterSubmitApprovals.size() - 1);
        assertEquals(ApprovalType.COURSE_ALLOCATION, req.getType());
        assertEquals(ApprovalStatus.PENDING, req.getStatus());
    }

    @Test
    @DisplayName("TEST D: Step 2 PO/PSO Dynamic Dimension & Target Benchmarks Persistence")
    void testStep2TargetBenchmarksPersistence() {
        // Create 3 POs and 2 PSOs dynamically
        ProgrammeOutcome savedPo1 = programmeOutcomeRepository.save(ProgrammeOutcome.builder().id("po-1-" + UUID.randomUUID().toString().substring(0, 4)).programmeBatchId(programmeBatchId1).code("PO1").statement("PO1 Stmt").build());
        programmeOutcomeRepository.save(ProgrammeOutcome.builder().id("po-2-" + UUID.randomUUID().toString().substring(0, 4)).programmeBatchId(programmeBatchId1).code("PO2").statement("PO2 Stmt").build());
        programmeOutcomeRepository.save(ProgrammeOutcome.builder().id("po-3-" + UUID.randomUUID().toString().substring(0, 4)).programmeBatchId(programmeBatchId1).code("PO3").statement("PO3 Stmt").build());
        ProgrammeSpecificOutcome savedPso1 = programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder().id("pso-1-" + UUID.randomUUID().toString().substring(0, 4)).programmeBatchId(programmeBatchId1).code("PSO1").statement("PSO1 Stmt").build());
        programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder().id("pso-2-" + UUID.randomUUID().toString().substring(0, 4)).programmeBatchId(programmeBatchId1).code("PSO2").statement("PSO2 Stmt").build());

        // Add competencies
        poCompetencyRepository.save(PoCompetency.builder().id("pocomp-1").poId(savedPo1.getId()).code("PO1.1").statement("Competency 1.1").build());
        psoCompetencyRepository.save(PsoCompetency.builder().id("psocomp-1").psoId(savedPso1.getId()).code("PSO1.1").statement("PSO Competency 1.1").build());

        Map<String, Object> outcomes = academicService.getConsolidatedOutcomes(progId1, programmeBatchId1);
        @SuppressWarnings("unchecked")
        List<ProgrammeOutcome> pos = (List<ProgrammeOutcome>) outcomes.get("pos");
        @SuppressWarnings("unchecked")
        List<ProgrammeSpecificOutcome> psos = (List<ProgrammeSpecificOutcome>) outcomes.get("psos");
        assertEquals(3, pos.size());
        assertEquals(2, psos.size());

        // Verify competencies are populated in consolidated outcomes
        assertNotNull(pos.get(0).getCompetencies());
        assertEquals(1, pos.get(0).getCompetencies().size());
        assertEquals("PO1.1", pos.get(0).getCompetencies().get(0).getCode());

        assertNotNull(psos.get(0).getCompetencies());
        assertEquals(1, psos.get(0).getCompetencies().size());
        assertEquals("PSO1.1", psos.get(0).getCompetencies().get(0).getCode());

        // Save targets via OutcomeService
        Map<String, BigDecimal> poTargets = Map.of("PO1", new BigDecimal("2.20"), "PO2", new BigDecimal("2.60"), "PO3", new BigDecimal("2.80"));
        Map<String, BigDecimal> psoTargets = Map.of("PSO1", new BigDecimal("2.40"), "PSO2", new BigDecimal("2.70"));
        outcomeService.saveProgrammeTargets(progId1, ProgrammeTargetDto.builder()
                .masterProgrammeId(progId1)
                .programmeBatchId(programmeBatchId1)
                .poTargets(poTargets)
                .psoTargets(psoTargets)
                .build());

        // Fetch back and verify
        ProgrammeTargetDto targetsDto = outcomeService.getProgrammeTargets(progId1);
        assertNotNull(targetsDto);
        assertEquals(new BigDecimal("2.20"), targetsDto.getPoTargets().get("PO1"));
        assertEquals(new BigDecimal("2.60"), targetsDto.getPoTargets().get("PO2"));
        assertEquals(new BigDecimal("2.40"), targetsDto.getPsoTargets().get("PSO1"));
    }

    @Test
    @DisplayName("TEST E: Save Consolidated Outcomes with Competencies via Payload")
    void testSaveConsolidatedOutcomesWithCompetencies() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("masterProgrammeId", progId1);
        payload.put("programmeBatchId", programmeBatchId1);

        Map<String, Object> po1 = new LinkedHashMap<>();
        po1.put("code", "PO1");
        po1.put("statement", "Engineering knowledge statement");
        po1.put("target", 2.8);
        po1.put("competencies", List.of(
                Map.of("code", "PO1.1", "statement", "Mathematics application"),
                Map.of("code", "PO1.2", "statement", "Computing principles")
        ));

        Map<String, Object> pso1 = new LinkedHashMap<>();
        pso1.put("code", "PSO1");
        pso1.put("statement", "Software systems engineering");
        pso1.put("target", 2.6);
        pso1.put("competencies", List.of(
                Map.of("code", "PSO1.1", "statement", "Scalable backend design")
        ));

        Map<String, Object> peo1 = new LinkedHashMap<>();
        peo1.put("code", "PEO1");
        peo1.put("statement", "Industry leadership");

        payload.put("pos", List.of(po1));
        payload.put("psos", List.of(pso1));
        payload.put("peos", List.of(peo1));

        Map<String, Object> result = academicService.saveConsolidatedOutcomes(payload);
        assertNotNull(result);

        Map<String, Object> savedData = academicService.getConsolidatedOutcomes(progId1, programmeBatchId1);
        @SuppressWarnings("unchecked")
        List<ProgrammeOutcome> pos = (List<ProgrammeOutcome>) savedData.get("pos");
        @SuppressWarnings("unchecked")
        List<ProgrammeSpecificOutcome> psos = (List<ProgrammeSpecificOutcome>) savedData.get("psos");
        @SuppressWarnings("unchecked")
        List<PeoOutcome> peos = (List<PeoOutcome>) savedData.get("peos");

        assertEquals(1, pos.size());
        assertEquals("PO1", pos.get(0).getCode());
        assertNotNull(pos.get(0).getCompetencies());
        assertEquals(2, pos.get(0).getCompetencies().size());
        assertEquals("PO1.1", pos.get(0).getCompetencies().get(0).getCode());
        assertEquals("PO1.2", pos.get(0).getCompetencies().get(1).getCode());

        assertEquals(1, psos.size());
        assertEquals("PSO1", psos.get(0).getCode());
        assertNotNull(psos.get(0).getCompetencies());
        assertEquals(1, psos.get(0).getCompetencies().size());
        assertEquals("PSO1.1", psos.get(0).getCompetencies().get(0).getCode());

        assertEquals(1, peos.size());
        assertEquals("PEO1", peos.get(0).getCode());
    }

    @Test
    @DisplayName("TEST F: Complete Workflow Transition")
    void testCompleteWorkflowTransition() {
        ProgrammeCoordinatorSetupProgressDto completed = academicService.completeProgrammeCoordinatorSetup(coordinatorEmail, progId1, programmeBatchId1);
        assertNotNull(completed);
        assertEquals(SetupStepStatus.COMPLETED, completed.getOverallStatus());
        assertTrue(completed.getCompletedSteps().containsAll(List.of("courses", "po_pso_target", "indirect_attainment", "programme_atr", "review")));
        assertTrue(completed.getPendingSteps().isEmpty());
    }
}
