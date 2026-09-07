package com.dypiu.nba.service;

import com.dypiu.nba.dto.CourseAtrReportDto;
import com.dypiu.nba.dto.ProgrammeAtrReportDto;
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Phase91RemediationIntegrationTest {

    @Autowired
    private AtrService atrService;

    @Autowired
    private BatchLifecycleService batchLifecycleService;

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
    private CourseAtrRepository courseAtrRepository;

    @Autowired
    private ProgrammeAtrRepository programmeAtrRepository;

    @MockBean
    private CurrentUserScopeService currentUserScopeService;

    private School schoolA;
    private Department deptA;
    private Department deptB;
    private MasterProgramme progA;
    private ProgrammeBatch batch2024;
    private ProgrammeBatch batch2028;
    private ProgrammeBatchCourse courseCN;
    private ProgrammeBatchCourse offering2024;
    private ProgrammeBatchCourse offering2028;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);

        schoolA = schoolRepository.save(School.builder()
                .id("sch-" + uid)
                .name("Engineering School")
                .code("ENG-" + uid)
                .build());

        deptA = departmentRepository.save(Department.builder()
                .id("dept-cs-" + uid)
                .name("Computer Science")
                .code("CS-" + uid)
                .schoolId(schoolA.getId())
                .build());

        deptB = departmentRepository.save(Department.builder()
                .id("dept-me-" + uid)
                .name("Mechanical Engineering")
                .code("ME-" + uid)
                .schoolId(schoolA.getId())
                .build());

        progA = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-cs-" + uid)
                .name("B.Tech Computer Science")
                .code("BTCS-" + uid)
                .departmentId(deptA.getId())
                .durationYears(4)
                .build());

        batch2024 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-" + uid)
                .masterProgrammeId(progA.getId())
                .name("2024-2028 Cohort")
                .startYear(2024)
                .endYear(2028)
                .status("GRADUATED")
                .build());

        batch2028 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2028-" + uid)
                .masterProgrammeId(progA.getId())
                .name("2028-2032 Cohort")
                .startYear(2028)
                .endYear(2032)
                .status("ACTIVE")
                .build());

        courseCN = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch2024.getId()).semester(1)
                .id("crs-cn-" + uid)
                .masterProgrammeId(progA.getId())
                .name("Computer Networks")
                .code("CS401")
                .credits(4)
                .build());

        offering2024 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-2024-" + uid)
                .masterCourseId(courseCN.getId())
                .code("CS401")
                .programmeBatchId(batch2024.getId())
                .semester(5)
                .courseCoordinatorId(101L)
                .courseCoordinatorName("Dr. Old Teacher")
                .build());

        offering2028 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-2028-" + uid)
                .masterCourseId(courseCN.getId())
                .code("CS401")
                .programmeBatchId(batch2028.getId())
                .semester(5)
                .courseCoordinatorId(202L)
                .courseCoordinatorName("Dr. New Teacher")
                .build());
    }

    @Test
    @DisplayName("Historical Snapshot Test: Approved Course ATR remains completely unchanged after current CO & target modifications")
    void testApprovedCourseAtrHistoricalSnapshotImmutability() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        CourseOutcome co1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-cn-1")
                .programmeBatchCourseId(offering2024.getId())
                .code("CO1")
                .statement("Understand networking fundamentals")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        CourseAtr atr1 = courseAtrRepository.save(CourseAtr.builder()
                .id("catr-2024-1")
                .programmeBatchCourseId(offering2024.getId())
                .coCode("CO1")
                .statement("Understand networking fundamentals")
                .targetScore(new BigDecimal("2.50"))
                .actualScore(new BigDecimal("2.10"))
                .pctAchieved(new BigDecimal("84.00"))
                .status(CourseAtrStatus.APPROVED)
                .actionsJson("[\"Increase practical labs\"]")
                .build());

        // Now modify current operational CourseOutcome target and statement
        co1.setStatement("MODIFIED STATEMENT 2026");
        co1.setTargetLevel(new BigDecimal("3.00"));
        courseOutcomeRepository.save(co1);

        // Fetch historical report
        CourseAtrReportDto report = atrService.getCourseAtrReport(offering2024.getId());

        assertNotNull(report);
        assertEquals(1, report.getOutcomes().size());
        CourseAtrReportDto.OutcomeRow row = report.getOutcomes().get(0);

        // Verify that historical snapshot values are preserved
        assertEquals("Understand networking fundamentals", row.getOutcomeStatement(), "Statement must remain the historical snapshot");
        assertEquals(new BigDecimal("2.50"), row.getTargetLevel(), "Target must remain the historical snapshot (2.50)");
        assertEquals(new BigDecimal("2.10"), row.getAttainmentLevel(), "Attainment must remain 2.10");
        assertEquals(new BigDecimal("84.00"), row.getAchievementPercentage(), "Percentage must remain 84.00%");
        assertEquals("APPROVED", report.getStatus());
    }

    @Test
    @DisplayName("Historical Lookup Test: Faculty assigned to 2028 offering can discover historical 2024 Course ATR without 403")
    void testFacultyHistoricalCourseAtrDiscovery() {
        CurrentUserScope facultyScope = CurrentUserScope.builder()
                .role(UserRole.FACULTY)
                .userId(202L)
                .email("newteacher@dypiu.ac.in")
                .name("Dr. New Teacher")
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(facultyScope);

        // Create historical Course ATR on 2024 offering
        courseAtrRepository.save(CourseAtr.builder()
                .id("catr-hist-2024")
                .programmeBatchCourseId(offering2024.getId())
                .coCode("CO1")
                .statement("Historic CO1")
                .targetScore(new BigDecimal("2.50"))
                .actualScore(new BigDecimal("2.20"))
                .pctAchieved(new BigDecimal("88.00"))
                .status(CourseAtrStatus.APPROVED)
                .build());

        // Faculty of 2028 batch queries historical Course ATRs by masterCourseId
        List<CourseAtrReportDto> historical = atrService.getHistoricalCourseAtrs(courseCN.getId());

        assertNotNull(historical);
        assertFalse(historical.isEmpty(), "Historical reports must be returned");
        assertEquals(offering2024.getId(), historical.get(0).getCourseOffering().getId());
    }

    @Test
    @DisplayName("Reopening Security Test: HOD from another department is rejected with 403 Forbidden")
    void testCrossDepartmentHodCannotReopenGraduatedBatch() {
        CurrentUserScope mechHodScope = CurrentUserScope.builder()
                .role(UserRole.HOD)
                .departmentId(deptB.getId()) // Mechanical Engineering HOD
                .schoolId(schoolA.getId())
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(mechHodScope);

        // Attempting to reopen CS batch (deptA) by Mechanical HOD (deptB)
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                batchLifecycleService.reopenGraduatedBatch(batch2024.getId(), ZonedDateTime.now().plusDays(5), "Legacy entry attempt"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("outside your assigned department scope"));
    }

    @Test
    @DisplayName("Reopening Window Expiration Test: Expired window prevents mutations")
    void testExpiredReopeningWindowBlocksMutation() {
        batch2024.setEditingWindowUntil(ZonedDateTime.now().minusHours(2));
        programmeBatchRepository.save(batch2024);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                batchLifecycleService.enforceBatchEditability(batch2024.getId()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("reopening window is currently active"));
    }
}
