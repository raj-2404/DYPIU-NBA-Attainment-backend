package com.dypiu.nba.service;

import com.dypiu.nba.controller.AcademicController;
import com.dypiu.nba.controller.MasterProgrammeController;
import com.dypiu.nba.controller.ProgrammeBatchController;
import com.dypiu.nba.controller.ProgrammeBatchCourseController;
import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.dto.CourseAttainmentReportDto;
import com.dypiu.nba.dto.CourseMappingMatrixDto;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Phase108ExplicitCanonicalDomainApiIntegrationTest {

    @Autowired
    private MasterProgrammeController masterProgrammeController;

    @Autowired
    private AcademicController academicController;

    @Autowired
    private ProgrammeBatchController programmeBatchController;

    @Autowired
    private ProgrammeBatchCourseController programmeBatchCourseController;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;
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
    private DepartmentRepository departmentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @MockBean
    private CurrentUserScopeService currentUserScopeService;

    private School school;
    private Department dept;
    private MasterProgramme prog;
    private ProgrammeBatch batch;
    private ProgrammeBatchCourse course;
    private ProgrammeBatchCourse pbc;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);

        school = schoolRepository.save(School.builder()
                .id("sch-canon-" + uid)
                .name("School of Computing")
                .code("SOC-" + uid)
                .build());

        dept = departmentRepository.save(Department.builder()
                .id("dept-canon-" + uid)
                .name("Department of CSE")
                .code("DCSE-" + uid)
                .schoolId(school.getId())
                .build());

        prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-canon-" + uid)
                .name("B.Tech Computer Science")
                .code("BTCS-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-canon-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028 Cohort")
                .startYear(2024)
                .endYear(2028)
                .status("ACTIVE")
                .build());

        course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("mc-canon-" + uid)
                .programmeBatchId(batch.getId())
                .name("Data Structures and Algorithms")
                .code("CS201")
                .credits(4)
                .semester(3)
                .build());

        pbc = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-canon-" + uid)
                .masterCourseId(course.getId())
                .programmeBatchId(batch.getId())
                .semester(3)
                .courseCoordinatorId(101L)
                .courseCoordinatorName("Dr. Knuth")
                .build());

        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-canon-" + uid)
                .programmeBatchCourseId(pbc.getId())
                .code("CO1")
                .statement("Analyze time and space complexity")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po-canon-" + uid)
                .programmeBatchId(batch.getId())
                .code("PO1")
                .statement("Engineering Knowledge")
                .target(new BigDecimal("2.50"))
                .build());

        programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                .id("pso-canon-" + uid)
                .programmeBatchId(batch.getId())
                .code("PSO1")
                .statement("Software Engineering Excellence")
                .target(new BigDecimal("2.50"))
                .build());
    }

    @Test
    @DisplayName("Phase 10.8: Canonical MasterProgramme & MasterCourse endpoints work correctly")
    void testMasterProgrammeAndCourseCanonicalEndpoints() {
        CurrentUserScope iqacScope = CurrentUserScope.builder().role(UserRole.IQAC).build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // MasterProgramme
        ResponseEntity<ApiResponse<MasterProgramme>> progRes = masterProgrammeController.getMasterProgrammeById(prog.getId());
        assertNotNull(progRes.getBody());
        assertEquals(prog.getId(), progRes.getBody().getData().getId());

        // MasterCourse
        ResponseEntity<ApiResponse<ProgrammeBatchCourse>> courseRes = academicController.getCourseById(course.getId());
        assertNotNull(courseRes.getBody());
        assertEquals(course.getId(), courseRes.getBody().getData().getId());
    }

    @Test
    @DisplayName("Phase 10.8: Canonical ProgrammeBatch reports & ATR endpoints work correctly")
    void testProgrammeBatchCanonicalEndpoints() {
        CurrentUserScope iqacScope = CurrentUserScope.builder().role(UserRole.IQAC).build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Programme Batch Report 1 (Avg Mapping)
        ResponseEntity<ApiResponse<Object>> r1Res = programmeBatchController.getAverageMappingReport(batch.getId());
        assertNotNull(r1Res.getBody());
        assertTrue(r1Res.getBody().isSuccess());

        // Programme Batch Main Attainment Report
        ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> mainRepRes = programmeBatchController.getProgrammeBatchAttainmentMainReport(batch.getId());
        assertNotNull(mainRepRes.getBody());
        assertEquals(batch.getId(), mainRepRes.getBody().getData().getProgrammeBatchId());

        // Finalize Programme Report
        ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> finRes = programmeBatchController.finalizeProgrammeBatchAttainmentReport(batch.getId(), null);
        assertNotNull(finRes.getBody());
        assertEquals(ReportStatus.FINALIZED, finRes.getBody().getData().getStatus());
    }

    @Test
    @DisplayName("Phase 10.8: Canonical ProgrammeBatchCourse COs, Mappings, Main Attainment Report & Finalize work correctly")
    void testProgrammeBatchCourseCanonicalEndpoints() {
        CurrentUserScope iqacScope = CurrentUserScope.builder().role(UserRole.IQAC).build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // COs
        ResponseEntity<ApiResponse<List<CourseOutcome>>> cosRes = programmeBatchCourseController.getCourseOutcomes(pbc.getId());
        assertNotNull(cosRes.getBody());
        assertFalse(cosRes.getBody().getData().isEmpty());

        // Mappings
        ResponseEntity<ApiResponse<CourseMappingMatrixDto>> mapRes = programmeBatchCourseController.getCourseMappings(pbc.getId());
        assertNotNull(mapRes.getBody());
        assertNotNull(mapRes.getBody().getData());

        // Course Attainment Main
        ResponseEntity<ApiResponse<CourseAttainmentReportDto>> attRes = programmeBatchCourseController.getCourseAttainmentMainReport(pbc.getId());
        assertNotNull(attRes.getBody());
        assertEquals(pbc.getId(), attRes.getBody().getData().getProgrammeBatchCourseId());

        // Finalize Course Attainment Main
        ResponseEntity<ApiResponse<CourseAttainmentReportDto>> finRes = programmeBatchCourseController.finalizeCourseAttainmentMainReport(pbc.getId(), null);
        assertNotNull(finRes.getBody());
        assertEquals(ReportStatus.FINALIZED, finRes.getBody().getData().getStatus());
    }
}
