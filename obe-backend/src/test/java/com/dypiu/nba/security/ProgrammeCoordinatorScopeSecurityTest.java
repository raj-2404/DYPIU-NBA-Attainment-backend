package com.dypiu.nba.security;

import com.dypiu.nba.controller.DashboardController;
import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.OutcomeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ProgrammeCoordinatorScopeSecurityTest {

    @Autowired
    private AcademicService academicService;

    @Autowired
    private OutcomeService outcomeService;

    @Autowired
    private AtrService atrService;

    @Autowired
    private DashboardController dashboardController;

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
    private Department deptA1;
    private Department deptA2;
    private Department deptB1;
    private MasterProgramme progA1; // PC A1 assigned
    private MasterProgramme progA2; // Same school, same dept, different prog
    private MasterProgramme progB1; // School B, Dept B1
    private ProgrammeBatch batchA1;
    private ProgrammeBatch batchA2;
    private ProgrammeBatch batchB1;
    private ProgrammeBatchCourse courseA1;
    private ProgrammeBatchCourse courseA2;
    private ProgrammeBatchCourse courseB1;

    private User pcA1;
    private User pcB1;

    @BeforeEach
    void setUpTestData() {
        SecurityContextHolder.clearContext();

        // 1. Schools
        schoolA = schoolRepository.save(School.builder()
                .id("sch-pc-a-" + System.nanoTime())
                .name("School of Engineering A")
                .code("SOE-A")
                .directorName("Director A")
                .directorEmail("director.a@dypiu.ac.in")
                .build());

        schoolB = schoolRepository.save(School.builder()
                .id("sch-pc-b-" + System.nanoTime())
                .name("School of Management B")
                .code("SOM-B")
                .directorName("Director B")
                .directorEmail("director.b@dypiu.ac.in")
                .build());

        // 2. Departments
        deptA1 = departmentRepository.save(Department.builder()
                .id("dept-pc-a1-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Computer Science A1")
                .code("CS-A1")
                .hod("HOD A1")
                .hodEmail("hod.a1@dypiu.ac.in")
                .build());

        deptA2 = departmentRepository.save(Department.builder()
                .id("dept-pc-a2-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Mechanical Engineering A2")
                .code("ME-A2")
                .hod("HOD A2")
                .hodEmail("hod.a2@dypiu.ac.in")
                .build());

        deptB1 = departmentRepository.save(Department.builder()
                .id("dept-pc-b1-" + System.nanoTime())
                .schoolId(schoolB.getId())
                .name("Finance B1")
                .code("FIN-B1")
                .hod("HOD B1")
                .hodEmail("hod.b1@dypiu.ac.in")
                .build());

        // 3. Programmes
        progA1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pc-a1-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech Computer Science")
                .code("BT-CS")
                .build());

        progA2 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pc-a2-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech AI & Data Science")
                .code("BT-AI")
                .build());

        progB1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pc-b1-" + System.nanoTime())
                .departmentId(deptB1.getId())
                .name("MBA Finance")
                .code("MBA-FIN")
                .build());

        // 4. Batches
        batchA1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pc-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("ProgrammeBatch CS 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .coordinatorEmail("pc.a1@dypiu.ac.in")
                .build());

        batchA2 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pc-a2-" + System.nanoTime())
                .masterProgrammeId(progA2.getId())
                .name("ProgrammeBatch AI 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .build());

        batchB1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pc-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("ProgrammeBatch MBA 2023-2025")
                .startYear(2023)
                .endYear(2025)
                .durationYears(2)
                .coordinatorEmail("pc.b1@dypiu.ac.in")
                .build());

        // 5. Courses
        courseA1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA1.getId()).semester(1)
                .id("crs-pc-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("Data Structures")
                .code("CS201")
                .credits(4)
                .courseType("CORE")
                .build());

        courseA2 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA2.getId()).semester(1)
                .id("crs-pc-a2-" + System.nanoTime())
                .masterProgrammeId(progA2.getId())
                .name("Machine Learning")
                .code("AI301")
                .credits(4)
                .courseType("CORE")
                .build());

        courseB1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchB1.getId()).semester(1)
                .id("crs-pc-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("Financial Accounting")
                .code("MBA101")
                .credits(3)
                .courseType("CORE")
                .build());

        // 6. Users
        pcA1 = userRepository.save(User.builder()
                .username("pc_a1")
                .email("pc.a1@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("PC A1")
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .masterProgrammeId(progA1.getId())
                .isActive(true)
                .build());

        pcB1 = userRepository.save(User.builder()
                .username("pc_b1")
                .email("pc.b1@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("PC B1")
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolB.getId())
                .departmentId(deptB1.getId())
                .masterProgrammeId(progB1.getId())
                .isActive(true)
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

    // =========================================================================
    // TEST CASES 1-25: PROGRAMME COORDINATOR SCOPE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Case 1: PC accesses own dashboard -> 200 OK with correct programme, batches, courses")
    void testCase1_PCAccessesOwnDashboard() {
        authenticateUser(pcA1);
        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                dashboardController.getProgrammeCoordinatorDashboard(progA1.getId(), null, null);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        Map<String, Object> data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(progA1.getId(), data.get("masterProgrammeId"));
    }

    @Test
    @DisplayName("Case 2: PC attempts to pass another programmeId to dashboard -> 403 Forbidden")
    void testCase2_PCAccessesOtherProgrammeDashboardForbidden() {
        authenticateUser(pcA1);
        // Attempting to access cross-programme in same dept
        assertThrows(ResponseStatusException.class, () ->
                dashboardController.getProgrammeCoordinatorDashboard(progA2.getId(), null, null)
        );
        // Attempting to access cross-school programme
        assertThrows(ResponseStatusException.class, () ->
                dashboardController.getProgrammeCoordinatorDashboard(progB1.getId(), null, null)
        );
    }

    @Test
    @DisplayName("Case 3: PC accesses programme summary / setup progress without params -> resolves own programme")
    void testCase3_PCAccessesSummaryAndProgressWithoutParams() {
        authenticateUser(pcA1);
        ProgrammeCoordinatorSummaryDto summary = academicService.getProgrammeCoordinatorSummary(null, null);
        assertNotNull(summary);
        assertEquals(progA1.getId(), summary.getMasterProgrammeId());

        ProgrammeCoordinatorSetupProgressDto progress = academicService.getProgrammeCoordinatorSetupProgress(null, null);
        assertNotNull(progress);
        assertEquals(progA1.getId(), progress.getMasterProgrammeId());
    }

    @Test
    @DisplayName("Case 4: PC updates setup progress for own programme -> 200 OK")
    void testCase4_PCUpdatesSetupProgressOwnProgramme() {
        authenticateUser(pcA1);
        ProgrammeCoordinatorSetupProgressDto updated =
                academicService.updateProgrammeCoordinatorSetupProgress(pcA1.getEmail(), progA1.getId(), 2);
        assertNotNull(updated);
        assertEquals(progA1.getId(), updated.getMasterProgrammeId());
        assertEquals(2, updated.getCurrentStep());
    }

    @Test
    @DisplayName("Case 5: PC updates setup progress for another programme -> 403 Forbidden")
    void testCase5_PCUpdatesSetupProgressOtherProgrammeForbidden() {
        authenticateUser(pcA1);
        assertThrows(ResponseStatusException.class, () ->
                academicService.updateProgrammeCoordinatorSetupProgress(pcA1.getEmail(), progB1.getId(), 2)
        );
        assertThrows(ResponseStatusException.class, () ->
                academicService.updateProgrammeCoordinatorSetupProgress(pcA1.getEmail(), progA2.getId(), 2)
        );
    }

    @Test
    @DisplayName("Case 6: PC gets all programmes -> returns only assigned programme")
    void testCase6_PCGetAllProgrammesReturnsOnlyAssigned() {
        authenticateUser(pcA1);
        List<MasterProgramme> programmes = academicService.getAllProgrammes();
        assertNotNull(programmes);
        assertEquals(1, programmes.size());
        assertEquals(progA1.getId(), programmes.get(0).getId());
    }

    @Test
    @DisplayName("Case 7: PC gets own programme by ID -> 200 OK")
    void testCase7_PCGetProgrammeByIdOwn() {
        authenticateUser(pcA1);
        MasterProgramme p = academicService.getProgrammeById(progA1.getId());
        assertNotNull(p);
        assertEquals(progA1.getId(), p.getId());
    }

    @Test
    @DisplayName("Case 8: PC gets another programme by ID (cross-dept or cross-school) -> 403 Forbidden")
    void testCase8_PCGetProgrammeByIdOtherForbidden() {
        authenticateUser(pcA1);
        assertThrows(ResponseStatusException.class, () ->
                academicService.getProgrammeById(progA2.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                academicService.getProgrammeById(progB1.getId())
        );
    }

    @Test
    @DisplayName("Case 9: PC gets all batches -> returns only batches for assigned programme")
    void testCase9_PCGetAllBatchesReturnsOnlyAssigned() {
        authenticateUser(pcA1);
        List<ProgrammeBatch> batches = academicService.getAllBatches();
        assertNotNull(batches);
        assertEquals(1, batches.size());
        assertEquals(batchA1.getId(), batches.get(0).getId());
    }

    @Test
    @DisplayName("Case 10: PC gets batches for assigned programme -> 200 OK")
    void testCase10_PCGetBatchesByAssignedProgramme() {
        authenticateUser(pcA1);
        List<ProgrammeBatch> batches = academicService.getBatchesByProgramme(progA1.getId());
        assertNotNull(batches);
        assertEquals(1, batches.size());
        assertEquals(batchA1.getId(), batches.get(0).getId());
    }

    @Test
    @DisplayName("Case 11: PC gets batches for another programme -> 403 Forbidden")
    void testCase11_PCGetBatchesByOtherProgrammeForbidden() {
        authenticateUser(pcA1);
        assertThrows(ResponseStatusException.class, () ->
                academicService.getBatchesByProgramme(progA2.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                academicService.getBatchesByProgramme(progB1.getId())
        );
    }

    @Test
    @DisplayName("Case 12: PC gets batch by ID for assigned programme -> 200 OK")
    void testCase12_PCGetBatchByIdAssigned() {
        authenticateUser(pcA1);
        ProgrammeBatch b = academicService.getBatchById(batchA1.getId());
        assertNotNull(b);
        assertEquals(batchA1.getId(), b.getId());
    }

    @Test
    @DisplayName("Case 13: PC gets batch by ID for another programme -> 403 Forbidden")
    void testCase13_PCGetBatchByIdOtherForbidden() {
        authenticateUser(pcA1);
        assertThrows(ResponseStatusException.class, () ->
                academicService.getBatchById(batchA2.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                academicService.getBatchById(batchB1.getId())
        );
    }

    @Test
    @DisplayName("Case 14: PC creates batch for assigned programme -> 200 OK")
    void testCase14_PCCreatesBatchForAssignedProgramme() {
        authenticateUser(pcA1);
        ProgrammeBatch newBatch = ProgrammeBatch.builder()
                .masterProgrammeId(progA1.getId())
                .name("ProgrammeBatch CS 2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build();
        ProgrammeBatch saved = academicService.saveBatch(newBatch);
        assertNotNull(saved);
        assertEquals(progA1.getId(), saved.getMasterProgrammeId());
    }

    @Test
    @DisplayName("Case 15: PC creates batch for another programme -> 403 Forbidden")
    void testCase15_PCCreatesBatchForOtherProgrammeForbidden() {
        authenticateUser(pcA1);
        ProgrammeBatch otherProgBatch = ProgrammeBatch.builder()
                .masterProgrammeId(progB1.getId())
                .name("ProgrammeBatch MBA 2024-2026")
                .startYear(2024)
                .endYear(2026)
                .durationYears(2)
                .build();
        assertThrows(ResponseStatusException.class, () ->
                academicService.saveBatch(otherProgBatch)
        );
    }

    @Test
    @DisplayName("Case 16: PC gets all courses -> returns only courses for assigned programme")
    void testCase16_PCGetAllCoursesReturnsOnlyAssigned() {
        authenticateUser(pcA1);
        List<ProgrammeBatchCourse> courses = academicService.getAllCourses();
        assertNotNull(courses);
        assertEquals(1, courses.size());
        assertEquals(courseA1.getId(), courses.get(0).getId());
    }

    @Test
    @DisplayName("Case 17: PC gets course by ID for assigned programme -> 200 OK")
    void testCase17_PCGetCourseByIdAssigned() {
        authenticateUser(pcA1);
        ProgrammeBatchCourse c = academicService.getCourseById(courseA1.getId());
        assertNotNull(c);
        assertEquals(courseA1.getId(), c.getId());
    }

    @Test
    @DisplayName("Case 18: PC gets course by ID for another programme -> 403 Forbidden")
    void testCase18_PCGetCourseByIdOtherForbidden() {
        authenticateUser(pcA1);
        assertThrows(ResponseStatusException.class, () ->
                academicService.getCourseById(courseA2.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                academicService.getCourseById(courseB1.getId())
        );
    }

    @Test
    @DisplayName("Case 19: PC creates course for assigned programme -> 200 OK")
    void testCase19_PCCreatesCourseForAssignedProgramme() {
        authenticateUser(pcA1);
        ProgrammeBatchCourse newCourse = ProgrammeBatchCourse.builder().programmeBatchId(batchA1.getId()).semester(1)
                .masterProgrammeId(progA1.getId())
                .name("Operating Systems")
                .code("CS301")
                .credits(4)
                .courseType("CORE")
                .build();
        ProgrammeBatchCourse saved = academicService.saveCourse(newCourse);
        assertNotNull(saved);
        assertEquals(progA1.getId(), saved.getMasterProgrammeId());
    }

    @Test
    @DisplayName("Case 20: PC creates course for another programme -> 403 Forbidden")
    void testCase20_PCCreatesCourseForOtherProgrammeForbidden() {
        authenticateUser(pcA1);
        ProgrammeBatchCourse otherProgCourse = ProgrammeBatchCourse.builder().programmeBatchId(batchB1.getId()).semester(1)
                .masterProgrammeId(progB1.getId())
                .name("Marketing Management")
                .code("MBA201")
                .credits(3)
                .courseType("CORE")
                .build();
        assertThrows(ResponseStatusException.class, () ->
                academicService.saveCourse(otherProgCourse)
        );
    }

    @Test
    @DisplayName("Case 21: PC accesses/saves POs/PSOs/PEOs/Targets for assigned programme -> 200 OK")
    void testCase21_PCAccessesAndSavesOutcomesForAssignedProgramme() {
        authenticateUser(pcA1);

        // POs
        List<ProgrammeOutcome> pos = outcomeService.getPOsByProgramme(progA1.getId());
        assertNotNull(pos);
        List<ProgrammeOutcome> savedPos = outcomeService.savePOs(progA1.getId(), pos);
        assertNotNull(savedPos);

        // PSOs
        List<ProgrammeSpecificOutcome> psos = outcomeService.getPSOsByProgramme(progA1.getId());
        assertNotNull(psos);
        List<ProgrammeSpecificOutcome> savedPsos = outcomeService.savePSOs(progA1.getId(), psos);
        assertNotNull(savedPsos);

        // PEOs
        List<PeoOutcome> peos = outcomeService.getPEOsByProgramme(progA1.getId());
        assertNotNull(peos);
        List<PeoOutcome> savedPeos = outcomeService.savePEOs(progA1.getId(), peos);
        assertNotNull(savedPeos);

        // MasterProgramme Targets
        ProgrammeTargetDto targetDto = ProgrammeTargetDto.builder()
                .masterProgrammeId(progA1.getId())
                .programmeBatchId(batchA1.getId())
                .poTargets(Map.of("PO1", new BigDecimal("2.50")))
                .psoTargets(Map.of("PSO1", new BigDecimal("2.60")))
                .build();
        ProgrammeTargetDto savedTargets = outcomeService.saveProgrammeTargets(progA1.getId(), targetDto);
        assertNotNull(savedTargets);
    }

    @Test
    @DisplayName("Case 22: PC accesses/saves POs/PSOs/PEOs/Targets for another programme -> 403 Forbidden")
    void testCase22_PCAccessesAndSavesOutcomesForOtherProgrammeForbidden() {
        authenticateUser(pcA1);

        // POs
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.getPOsByProgramme(progB1.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.savePOs(progB1.getId(), Collections.emptyList())
        );

        // PSOs
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.getPSOsByProgramme(progB1.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.savePSOs(progB1.getId(), Collections.emptyList())
        );

        // PEOs
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.getPEOsByProgramme(progB1.getId())
        );
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.savePEOs(progB1.getId(), Collections.emptyList())
        );

        // Targets
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.getProgrammeTargets(progB1.getId())
        );
        ProgrammeTargetDto targetDto = ProgrammeTargetDto.builder()
                .masterProgrammeId(progB1.getId())
                .programmeBatchId(batchB1.getId())
                .poTargets(Map.of("PO1", new BigDecimal("2.50")))
                .build();
        assertThrows(ResponseStatusException.class, () ->
                outcomeService.saveProgrammeTargets(progB1.getId(), targetDto)
        );
    }

    @Test
    @DisplayName("Case 23: PC allocates courses for assigned programme -> 200 OK")
    void testCase23_PCAllocatesCoursesForAssignedProgramme() {
        authenticateUser(pcA1);
        List<Map<String, Object>> allocations = new ArrayList<>();
        Map<String, Object> allocItem = new HashMap<>();
        allocItem.put("masterCourseId", courseA1.getId());
        allocItem.put("facultyId", pcA1.getId());
        allocItem.put("academicYear", "2025-26");
        allocItem.put("semester", "SEM_3");
        allocItem.put("programmeBatchId", batchA1.getId());
        allocations.add(allocItem);

        Map<String, Object> res = academicService.allocateCourses(progA1.getId(), null, allocations);
        assertNotNull(res);
        assertTrue(Boolean.TRUE.equals(res.get("success")));
    }

    @Test
    @DisplayName("Case 24: PC allocates courses for another programme or with cross-programme course -> 403 Forbidden")
    void testCase24_PCAllocatesCoursesForOtherProgrammeOrCrossCourseForbidden() {
        authenticateUser(pcA1);

        // Other programme
        List<Map<String, Object>> allocB = new ArrayList<>();
        Map<String, Object> itemB = new HashMap<>();
        itemB.put("masterCourseId", courseB1.getId());
        itemB.put("facultyId", pcA1.getId());
        itemB.put("programmeBatchId", batchB1.getId());
        allocB.add(itemB);

        assertThrows(ResponseStatusException.class, () ->
                academicService.allocateCourses(progB1.getId(), null, allocB)
        );

        // Assigned programme, but cross-programme course (courseB1 inside progA1 call)
        List<Map<String, Object>> allocCross = new ArrayList<>();
        Map<String, Object> itemCross = new HashMap<>();
        itemCross.put("masterCourseId", courseB1.getId());
        itemCross.put("facultyId", pcA1.getId());
        itemCross.put("programmeBatchId", batchA1.getId());
        allocCross.add(itemCross);

        assertThrows(ResponseStatusException.class, () ->
                academicService.allocateCourses(progA1.getId(), null, allocCross)
        );
    }

    @Test
    @DisplayName("Case 25: PC accesses/saves MasterProgramme ATR for assigned programme -> 200 OK; another programme -> 403 Forbidden")
    void testCase25_PCProgrammeAtrScopeEnforcement() {
        authenticateUser(pcA1);

        // Ensure batch is completed for ATR modification
        batchA1.setStatus("COMPLETED");
        programmeBatchRepository.save(batchA1);

        // Own programme ATR report -> 200 OK
        ProgrammeAtrReportDto report = atrService.getProgrammeAtrReport(progA1.getId(), batchA1.getId());
        assertNotNull(report);
        assertEquals(progA1.getId(), report.getProgramme().getId());

        // Save own programme ATR report -> 200 OK
        ProgrammeAtrReportDto savedReport = atrService.saveProgrammeAtrReport(report);
        assertNotNull(savedReport);

        // Other programme ATR report -> 403 Forbidden
        assertThrows(ResponseStatusException.class, () ->
                atrService.getProgrammeAtrReport(progB1.getId(), batchB1.getId())
        );

        // Save other programme ATR report -> 403 Forbidden
        ProgrammeAtrReportDto otherDto = ProgrammeAtrReportDto.builder()
                .programme(ProgrammeAtrReportDto.ProgrammeSummary.builder().id(progB1.getId()).build())
                .batch(ProgrammeAtrReportDto.BatchSummary.builder().id(batchB1.getId()).build())
                .build();
        assertThrows(ResponseStatusException.class, () ->
                atrService.saveProgrammeAtrReport(otherDto)
        );
    }
}
