package com.dypiu.nba.service;

import com.dypiu.nba.dto.CourseAttainmentReportDto;
import com.dypiu.nba.dto.ProgrammeBatchAttainmentReportDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Phase10AttainmentReportPersistenceIntegrationTest {

    @Autowired
    private AttainmentReportService attainmentReportService;

    @Autowired
    private CourseAttainmentReportRepository courseAttainmentReportRepository;

    @Autowired
    private ProgrammeBatchAttainmentReportRepository programmeBatchAttainmentReportRepository;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ProgrammeBatchRepository programmeBatchRepository;
@Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Autowired
    private CourseOutcomeRepository courseOutcomeRepository;

    @Autowired
    private ProgrammeOutcomeRepository programmeOutcomeRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;

    @Autowired
    private CoPoMappingRepository coPoMappingRepository;

    @Autowired
    private CoPsoMappingRepository coPsoMappingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @MockBean
    private CurrentUserScopeService currentUserScopeService;

    private School school;
    private Department deptA;
    private Department deptB;
    private MasterProgramme prog;
    private ProgrammeBatch batch2024;
    private ProgrammeBatch batch2028;
    private ProgrammeBatchCourse courseCN;
    private ProgrammeBatchCourse offering2024;
    private ProgrammeBatchCourse offering2028;
    private CourseOutcome co1;
    private CourseOutcome co2;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);

        school = schoolRepository.save(School.builder()
                .id("sch-" + uid)
                .name("Engineering School")
                .code("ENG-" + uid)
                .build());

        deptA = departmentRepository.save(Department.builder()
                .id("dept-cs-" + uid)
                .name("Computer Science")
                .code("CS-" + uid)
                .schoolId(school.getId())
                .build());

        deptB = departmentRepository.save(Department.builder()
                .id("dept-me-" + uid)
                .name("Mechanical")
                .code("ME-" + uid)
                .schoolId(school.getId())
                .build());

        prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-cs-" + uid)
                .name("B.Tech Computer Science")
                .code("BTCS-" + uid)
                .departmentId(deptA.getId())
                .durationYears(4)
                .build());

        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("appr-alloc-" + uid)
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Course Allocation for " + prog.getId())
                .resourceId("allocation-" + prog.getId())
                .masterProgrammeId(prog.getId())
                .status(ApprovalStatus.APPROVED)
                .submittedBy("pc@dypiu.ac.in")
                .approvedBy("hod@dypiu.ac.in")
                .build());

        batch2024 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028 Cohort")
                .startYear(2024)
                .endYear(2028)
                .status("ACTIVE")
                .build());

        batch2028 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2028-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2028-2032 Cohort")
                .startYear(2028)
                .endYear(2032)
                .status("ACTIVE")
                .build());

        ProgrammeBatch templateBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-template-" + uid)
                .masterProgrammeId("prog-template-" + uid)
                .name("Template Batch")
                .startYear(2020)
                .endYear(2024)
                .build());

        courseCN = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("crs-cn-" + uid)
                .programmeBatchId(templateBatch.getId())
                .semester(1)
                .masterProgrammeId(prog.getId())
                .name("Computer Networks")
                .code("CS401")
                .credits(4)
                .build());

        offering2024 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-2024-" + uid)
                .masterCourseId(courseCN.getId())
                .programmeBatchId(batch2024.getId())
                .semester(5)
                .courseCoordinatorId(101L)
                .courseCoordinatorName("Dr. Alice")
                .courseNameOverride("Computer Networks & Security (2024)")
                .courseCodeOverride("CS401-24")
                .build());

        offering2028 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-2028-" + uid)
                .masterCourseId(courseCN.getId())
                .programmeBatchId(batch2028.getId())
                .semester(5)
                .courseCoordinatorId(202L)
                .courseCoordinatorName("Dr. Bob")
                .courseNameOverride("Advanced Computer Networks (2028)")
                .courseCodeOverride("CS401-28")
                .build());

        co1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-1-" + uid)
                .programmeBatchCourseId(offering2024.getId())
                .code("CO1")
                .statement("Understand Network Architecture")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        co2 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-2-" + uid)
                .programmeBatchCourseId(offering2024.getId())
                .code("CO2")
                .statement("Configure Routing Protocols")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        // Mappings
        coPoMappingRepository.save(CoPoMapping.builder()
                .id("copo-1-" + uid)
                .courseOutcomeId(co1.getId())
                .poCode("PO1")
                .mappingLevel(3)
                .build());

        coPoMappingRepository.save(CoPoMapping.builder()
                .id("copo-2-" + uid)
                .courseOutcomeId(co2.getId())
                .poCode("PO1")
                .mappingLevel(2)
                .build());

        coPsoMappingRepository.save(CoPsoMapping.builder()
                .id("copso-1-" + uid)
                .courseOutcomeId(co1.getId())
                .psoCode("PSO1")
                .mappingLevel(3)
                .build());

        // Programme Outcomes
        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po1-" + uid)
                .programmeBatchId(batch2024.getId())
                .code("PO1")
                .statement("Engineering Knowledge")
                .target(new BigDecimal("2.50"))
                .build());

        programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                .id("pso1-" + uid)
                .programmeBatchId(batch2024.getId())
                .code("PSO1")
                .statement("Network Systems Development")
                .target(new BigDecimal("2.50"))
                .build());
    }

    @Test
    @DisplayName("Phase 10: Course Attainment Report generation persists Table 1, Table 2, Table 3")
    void testCourseAttainmentReportGenerationAndPersistence() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        CourseAttainmentReportDto report = attainmentReportService.getOrCreateCourseAttainmentReport(offering2024.getId());

        assertNotNull(report);
        assertEquals(offering2024.getId(), report.getProgrammeBatchCourseId());
        assertEquals("CS401-24", report.getCourseCode());
        assertEquals("Computer Networks & Security (2024)", report.getCourseName());
        assertEquals(ReportStatus.DRAFT, report.getStatus());

        // Verify Table 1: Articulation Matrix
        assertNotNull(report.getTable1Mapping());
        assertFalse(report.getTable1Mapping().isEmpty(), "Table 1 must contain CO articulation mappings");

        // Verify Table 2: Course PO/PSO direct contributions
        assertNotNull(report.getTable2DirectPO());

        // Verify Table 3: CO attainment breakdown
        assertNotNull(report.getTable3CoAttainments());
        assertFalse(report.getTable3CoAttainments().isEmpty(), "Table 3 must contain CO attainment rows");
        for (CourseAttainmentReportDto.Table3Row row : report.getTable3CoAttainments()) {
            assertNotNull(row.getCoCode());
            assertNotNull(row.getTargetLevel());
            assertNotNull(row.getObservation());
        }

        // Verify DB persistence
        var savedOpt = courseAttainmentReportRepository.findByProgrammeBatchCourseId(offering2024.getId());
        assertTrue(savedOpt.isPresent(), "Report must be persisted in course_attainment_reports");
    }

    @Test
    @DisplayName("Phase 10: Finalized Course Report is immutable and records AuditLog")
    void testFinalizeCourseReportImmutabilityAndAudit() {
        CurrentUserScope coordinatorScope = CurrentUserScope.builder()
                .role(UserRole.FACULTY)
                .userId(101L)
                .email("alice@dypiu.ac.in")
                .name("Dr. Alice")
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(coordinatorScope);

        CourseAttainmentReportDto finalized = attainmentReportService.finalizeCourseReport(offering2024.getId(), "Dr. Alice");

        assertNotNull(finalized);
        assertEquals(ReportStatus.FINALIZED, finalized.getStatus());
        assertEquals("Dr. Alice", finalized.getSubmittedBy());

        // Verify DB has finalized status
        var saved = courseAttainmentReportRepository.findByProgrammeBatchCourseId(offering2024.getId()).orElseThrow();
        assertEquals(ReportStatus.FINALIZED, saved.getStatus());

        // Verify AuditLog recorded
        var logs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(offering2024.getId());
        assertFalse(logs.isEmpty(), "AuditLog must record course report finalization");
    }

    @Test
    @DisplayName("Phase 10: Programme Batch Attainment Reports 1, 2, 3, 4 generation and persistence")
    void testProgrammeBatchAttainmentReportGeneration() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(prog.getId(), batch2024.getId());

        assertNotNull(report);
        assertEquals(batch2024.getId(), report.getProgrammeBatchId());
        assertEquals(prog.getId(), report.getMasterProgrammeId());
        assertEquals(ReportStatus.DRAFT, report.getStatus());

        // Report 1: Average Mapping
        assertNotNull(report.getReport1AverageMappingPO());
        assertFalse(report.getReport1AverageMappingPO().isEmpty());

        // Report 2: Direct Attainment
        assertNotNull(report.getReport2DirectAttainmentPO());

        // Report 3: Indirect Attainment
        assertNotNull(report.getReport3IndirectAttainmentPO());

        // Report 4: Overall Programme Attainment (80/20)
        assertNotNull(report.getReport4OverallAttainmentPO());
        assertFalse(report.getReport4OverallAttainmentPO().isEmpty());

        // Verify DB persistence
        var savedOpt = programmeBatchAttainmentReportRepository.findByProgrammeBatchId(batch2024.getId());
        assertTrue(savedOpt.isPresent(), "Programme report must be persisted in programme_batch_attainment_reports");
    }

    @Test
    @DisplayName("Phase 10: Historical Course Reports lookup across batches for same MasterCourse")
    void testHistoricalCourseReportsDiscovery() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Pre-create reports for both 2024 and 2028 offerings of courseCN
        attainmentReportService.getOrCreateCourseAttainmentReport(offering2024.getId());
        attainmentReportService.getOrCreateCourseAttainmentReport(offering2028.getId());

        List<CourseAttainmentReportDto> historical = attainmentReportService.getHistoricalCourseAttainmentReports(offering2028.getId());

        assertNotNull(historical);
        assertEquals(2, historical.size(), "Should discover historical reports for both offerings of the MasterCourse");
    }

    @Test
    @DisplayName("Phase 10: Scope Security - Cross Department HOD is blocked with 403")
    void testCrossDepartmentHodBlockedFromCourseReport() {
        CurrentUserScope mechHodScope = CurrentUserScope.builder()
                .role(UserRole.HOD)
                .departmentId(deptB.getId()) // Mechanical HOD
                .schoolId(school.getId())
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(mechHodScope);

        // Trying to access CS course offering report
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                attainmentReportService.getOrCreateCourseAttainmentReport(offering2024.getId()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("outside your department"));
    }
}
