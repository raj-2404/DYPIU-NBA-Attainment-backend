package com.dypiu.nba.security;

import com.dypiu.nba.controller.DashboardController;
import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.dto.DirectorSchoolSummaryDto;
import com.dypiu.nba.dto.DirectorSetupProgressDto;
import com.dypiu.nba.dto.HodDepartmentSummaryDto;
import com.dypiu.nba.dto.HodSetupProgressDto;
import com.dypiu.nba.dto.UserDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DirectorAndHodScopeSecurityTest {

    @Autowired
    private AcademicService academicService;

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
    private MasterProgramme progA1;
    private MasterProgramme progB1;
    private ProgrammeBatch batchA1;
    private ProgrammeBatch batchB1;
    private ProgrammeBatchCourse courseA1;
    private ProgrammeBatchCourse courseB1;

    private User directorA;
    private User directorB;
    private User hodA1;
    private User hodA2;
    private User iqacUser;

    @BeforeEach
    void setUpTestData() {
        SecurityContextHolder.clearContext();

        // 1. Schools
        schoolA = schoolRepository.save(School.builder()
                .id("sch-test-a-" + System.nanoTime())
                .name("School of Engineering A")
                .code("SOE-A")
                .directorName("Director A")
                .directorEmail("director.a@dypiu.ac.in")
                .build());

        schoolB = schoolRepository.save(School.builder()
                .id("sch-test-b-" + System.nanoTime())
                .name("School of Management B")
                .code("SOM-B")
                .directorName("Director B")
                .directorEmail("director.b@dypiu.ac.in")
                .build());

        // 2. Departments
        deptA1 = departmentRepository.save(Department.builder()
                .id("dept-test-a1-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Computer Science A1")
                .code("CS-A1")
                .hod("HOD A1")
                .hodEmail("hod.a1@dypiu.ac.in")
                .build());

        deptA2 = departmentRepository.save(Department.builder()
                .id("dept-test-a2-" + System.nanoTime())
                .schoolId(schoolA.getId())
                .name("Mechanical Engineering A2")
                .code("ME-A2")
                .hod("HOD A2")
                .hodEmail("hod.a2@dypiu.ac.in")
                .build());

        deptB1 = departmentRepository.save(Department.builder()
                .id("dept-test-b1-" + System.nanoTime())
                .schoolId(schoolB.getId())
                .name("Finance B1")
                .code("FIN-B1")
                .hod("HOD B1")
                .hodEmail("hod.b1@dypiu.ac.in")
                .build());

        // 3. Programmes
        progA1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-test-a1-" + System.nanoTime())
                .departmentId(deptA1.getId())
                .name("B.Tech Computer Science")
                .code("BT-CS")
                .build());

        progB1 = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-test-b1-" + System.nanoTime())
                .departmentId(deptB1.getId())
                .name("MBA Finance")
                .code("MBA-FIN")
                .build());

        // 4. Batches
        batchA1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-test-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("ProgrammeBatch CS 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .build());

        batchB1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-test-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("ProgrammeBatch MBA 2023-2025")
                .startYear(2023)
                .endYear(2025)
                .durationYears(2)
                .build());

        // 5. Courses
        courseA1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA1.getId()).semester(1)
                .id("crs-test-a1-" + System.nanoTime())
                .masterProgrammeId(progA1.getId())
                .name("Data Structures")
                .code("CS201")
                .credits(4)
                .courseType("CORE")
                .build());

        courseB1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchB1.getId()).semester(1)
                .id("crs-test-b1-" + System.nanoTime())
                .masterProgrammeId(progB1.getId())
                .name("Financial Accounting")
                .code("MBA101")
                .credits(3)
                .courseType("CORE")
                .build());

        // 6. Users
        directorA = userRepository.save(User.builder()
                .username("director_a")
                .email("director.a@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Director A")
                .role(UserRole.DIRECTOR)
                .schoolId(schoolA.getId())
                .isActive(true)
                .build());

        directorB = userRepository.save(User.builder()
                .username("director_b")
                .email("director.b@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Director B")
                .role(UserRole.DIRECTOR)
                .schoolId(schoolB.getId())
                .isActive(true)
                .build());

        hodA1 = userRepository.save(User.builder()
                .username("hod_a1")
                .email("hod.a1@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("HOD A1")
                .role(UserRole.HOD)
                .schoolId(schoolA.getId())
                .departmentId(deptA1.getId())
                .isActive(true)
                .build());

        hodA2 = userRepository.save(User.builder()
                .username("hod_a2")
                .email("hod.a2@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("HOD A2")
                .role(UserRole.HOD)
                .schoolId(schoolA.getId())
                .departmentId(deptA2.getId())
                .isActive(true)
                .build());

        iqacUser = userRepository.save(User.builder()
                .username("iqac_user_dh")
                .email("iqac.dh@dypiu.ac.in")
                .passwordHash("test_hash")
                .name("Global IQAC")
                .role(UserRole.IQAC)
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
    // SCENARIOS 1-13: DIRECTOR SCOPE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Scenario 1: Director A accesses Director Dashboard scoped to School A")
    void testDirectorAccessDashboardSchoolA() {
        authenticateUser(directorA);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getDirectorDashboard(null, null, null);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        Map<String, Object> data = response.getBody().getData();
        School school = (School) data.get("school");
        assertNotNull(school);
        assertEquals(schoolA.getId(), school.getId());
    }

    @Test
    @DisplayName("Scenario 2: Director A attempting schoolId bypass gets School A dashboard")
    void testDirectorSchoolBypassAttemptIgnored() {
        authenticateUser(directorA);
        // Explicitly passing schoolB in query parameter should be ignored in favor of Director A's authoritative scope
        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getDirectorDashboard(schoolB.getId(), null, null);
        assertNotNull(response.getBody());
        Map<String, Object> data = response.getBody().getData();
        School school = (School) data.get("school");
        assertNotNull(school);
        assertEquals(schoolA.getId(), school.getId());
    }

    @Test
    @DisplayName("Scenario 3: Director A sees only departments belonging to School A")
    void testDirectorDepartmentsScopedToSchoolA() {
        authenticateUser(directorA);
        List<Department> depts = academicService.getAllDepartments();
        assertFalse(depts.isEmpty());
        assertTrue(depts.stream().allMatch(d -> schoolA.getId().equals(d.getSchoolId())));
        assertTrue(depts.stream().anyMatch(d -> deptA1.getId().equals(d.getId())));
        assertTrue(depts.stream().anyMatch(d -> deptA2.getId().equals(d.getId())));
        assertFalse(depts.stream().anyMatch(d -> deptB1.getId().equals(d.getId())));
    }

    @Test
    @DisplayName("Scenario 4: Director A cannot access or delete department in School B (403 Forbidden)")
    void testDirectorCannotAccessSchoolBDepartment() {
        authenticateUser(directorA);
        assertThrows(ResponseStatusException.class, () -> academicService.getDepartmentById(deptB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteDepartment(deptB1.getId()));
    }

    @Test
    @DisplayName("Scenario 5: Director A sees only programmes under School A")
    void testDirectorProgrammesScopedToSchoolA() {
        authenticateUser(directorA);
        List<MasterProgramme> progs = academicService.getAllProgrammes();
        assertFalse(progs.isEmpty());
        assertTrue(progs.stream().anyMatch(p -> progA1.getId().equals(p.getId())));
        assertFalse(progs.stream().anyMatch(p -> progB1.getId().equals(p.getId())));
    }

    @Test
    @DisplayName("Scenario 6: Director A cannot access or delete programme in School B (403 Forbidden)")
    void testDirectorCannotAccessSchoolBProgramme() {
        authenticateUser(directorA);
        assertThrows(ResponseStatusException.class, () -> academicService.getProgrammeById(progB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteProgramme(progB1.getId()));
    }

    @Test
    @DisplayName("Scenario 7: Director A sees only batches under School A")
    void testDirectorBatchesScopedToSchoolA() {
        authenticateUser(directorA);
        List<ProgrammeBatch> batches = academicService.getAllBatches();
        assertFalse(batches.isEmpty());
        assertTrue(batches.stream().anyMatch(b -> batchA1.getId().equals(b.getId())));
        assertFalse(batches.stream().anyMatch(b -> batchB1.getId().equals(b.getId())));
    }

    @Test
    @DisplayName("Scenario 8: Director A cannot access or delete batch in School B (403 Forbidden)")
    void testDirectorCannotAccessSchoolBBatch() {
        authenticateUser(directorA);
        assertThrows(ResponseStatusException.class, () -> academicService.getBatchById(batchB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteBatch(batchB1.getId()));
    }

    @Test
    @DisplayName("Scenario 9: Director A sees only courses under School A")
    void testDirectorCoursesScopedToSchoolA() {
        authenticateUser(directorA);
        List<ProgrammeBatchCourse> courses = academicService.getAllCourses();
        assertFalse(courses.isEmpty());
        assertTrue(courses.stream().anyMatch(c -> courseA1.getId().equals(c.getId())));
        assertFalse(courses.stream().anyMatch(c -> courseB1.getId().equals(c.getId())));
    }

    @Test
    @DisplayName("Scenario 10: Director A cannot access or delete course in School B (403 Forbidden)")
    void testDirectorCannotAccessSchoolBCourse() {
        authenticateUser(directorA);
        assertThrows(ResponseStatusException.class, () -> academicService.getCourseById(courseB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteCourse(courseB1.getId()));
    }

    @Test
    @DisplayName("Scenario 11: Director A sees only users belonging to School A")
    void testDirectorUsersScopedToSchoolA() {
        authenticateUser(directorA);
        List<UserDto> users = academicService.getUsersByRole("ALL");
        assertFalse(users.isEmpty());
        assertTrue(users.stream().allMatch(u -> schoolA.getId().equals(u.getSchoolId())));
        assertTrue(users.stream().anyMatch(u -> directorA.getId().equals(u.getId())));
        assertTrue(users.stream().anyMatch(u -> hodA1.getId().equals(u.getId())));
        assertFalse(users.stream().anyMatch(u -> directorB.getId().equals(u.getId())));
    }

    @Test
    @DisplayName("Scenario 12: Director A setup progress is strictly isolated to School A")
    void testDirectorSetupProgressIsolated() {
        authenticateUser(directorA);
        DirectorSetupProgressDto progress = academicService.getDirectorSetupProgress(schoolA.getId(), directorA.getEmail());
        assertEquals(schoolA.getId(), progress.getSchoolId());

        // Attempting to access setup progress for School B must throw 403 FORBIDDEN
        assertThrows(ResponseStatusException.class, () -> academicService.getDirectorSetupProgress(schoolB.getId(), null));
    }

    @Test
    @DisplayName("Scenario 13: Director A school summary is strictly isolated to School A")
    void testDirectorSchoolSummaryIsolated() {
        authenticateUser(directorA);
        DirectorSchoolSummaryDto summary = academicService.getDirectorSchoolSummary(directorA.getEmail());
        assertEquals(schoolA.getId(), summary.getSchoolId());
        assertEquals(schoolA.getName(), summary.getSchoolName());
    }

    // =========================================================================
    // SCENARIOS 14-22: HOD SCOPE ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Scenario 14: HOD A1 accesses HOD Dashboard for Department A1")
    void testHodAccessDashboardDeptA1() {
        authenticateUser(hodA1);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getHodDashboard(null, null, null);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        Map<String, Object> data = response.getBody().getData();
        Department dept = (Department) data.get("department");
        assertNotNull(dept);
        assertEquals(deptA1.getId(), dept.getId());
        assertEquals(schoolA.getId(), dept.getSchoolId());
    }

    @Test
    @DisplayName("Scenario 15: HOD A1 cannot access HOD Dashboard of another department (403 Forbidden / strictly scoped)")
    void testHodCannotAccessOtherDepartmentDashboard() {
        authenticateUser(hodA1);
        // Parameter tampering with deptA2 should be overridden by HOD A1's authoritative scope
        ResponseEntity<ApiResponse<Map<String, Object>>> response = dashboardController.getHodDashboard(deptA2.getId(), null, null);
        assertNotNull(response.getBody());
        Map<String, Object> data = response.getBody().getData();
        Department dept = (Department) data.get("department");
        assertNotNull(dept);
        assertEquals(deptA1.getId(), dept.getId());
    }

    @Test
    @DisplayName("Scenario 16: HOD A1 sees only programmes belonging to Department A1")
    void testHodProgrammesScopedToDeptA1() {
        authenticateUser(hodA1);
        List<MasterProgramme> progs = academicService.getAllProgrammes();
        assertFalse(progs.isEmpty());
        assertTrue(progs.stream().allMatch(p -> deptA1.getId().equals(p.getDepartmentId())));
        assertTrue(progs.stream().anyMatch(p -> progA1.getId().equals(p.getId())));
        assertFalse(progs.stream().anyMatch(p -> progB1.getId().equals(p.getId())));
    }

    @Test
    @DisplayName("Scenario 17: HOD A1 cannot access or delete programme in Dept B1 (403 Forbidden)")
    void testHodCannotAccessDeptB1Programme() {
        authenticateUser(hodA1);
        assertThrows(ResponseStatusException.class, () -> academicService.getProgrammeById(progB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteProgramme(progB1.getId()));
    }

    @Test
    @DisplayName("Scenario 18: HOD A1 sees only batches belonging to programmes in Department A1")
    void testHodBatchesScopedToDeptA1() {
        authenticateUser(hodA1);
        List<ProgrammeBatch> batches = academicService.getAllBatches();
        assertFalse(batches.isEmpty());
        assertTrue(batches.stream().anyMatch(b -> batchA1.getId().equals(b.getId())));
        assertFalse(batches.stream().anyMatch(b -> batchB1.getId().equals(b.getId())));
    }

    @Test
    @DisplayName("Scenario 19: HOD A1 cannot access or delete batch in Dept B1 (403 Forbidden)")
    void testHodCannotAccessDeptB1Batch() {
        authenticateUser(hodA1);
        assertThrows(ResponseStatusException.class, () -> academicService.getBatchById(batchB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteBatch(batchB1.getId()));
    }

    @Test
    @DisplayName("Scenario 20: HOD A1 sees only courses belonging to programmes in Department A1")
    void testHodCoursesScopedToDeptA1() {
        authenticateUser(hodA1);
        List<ProgrammeBatchCourse> courses = academicService.getAllCourses();
        assertFalse(courses.isEmpty());
        assertTrue(courses.stream().anyMatch(c -> courseA1.getId().equals(c.getId())));
        assertFalse(courses.stream().anyMatch(c -> courseB1.getId().equals(c.getId())));
    }

    @Test
    @DisplayName("Scenario 21: HOD A1 cannot access or delete course in Dept B1 (403 Forbidden)")
    void testHodCannotAccessDeptB1Course() {
        authenticateUser(hodA1);
        assertThrows(ResponseStatusException.class, () -> academicService.getCourseById(courseB1.getId()));
        assertThrows(ResponseStatusException.class, () -> academicService.deleteCourse(courseB1.getId()));
    }

    @Test
    @DisplayName("Scenario 22: HOD A1 setup progress & department summary are strictly isolated to Department A1")
    void testHodSummaryAndProgressIsolated() {
        authenticateUser(hodA1);
        HodDepartmentSummaryDto summary = academicService.getHodDepartmentSummary(deptA1.getId(), hodA1.getEmail());
        assertEquals(deptA1.getId(), summary.getDeptId());

        HodSetupProgressDto progress = academicService.getHodSetupProgress(deptA1.getId(), hodA1.getEmail());
        assertEquals(deptA1.getId(), progress.getDepartmentId());

        // Accessing other department's summary must throw 403 Forbidden
        assertThrows(ResponseStatusException.class, () -> academicService.getHodDepartmentSummary(deptA2.getId(), null));
        assertThrows(ResponseStatusException.class, () -> academicService.getHodDepartmentSummary(deptB1.getId(), null));
    }

    // =========================================================================
    // SCENARIO 23: IQAC UNRESTRICTED ACCESS
    // =========================================================================

    @Test
    @DisplayName("Scenario 23: IQAC has unrestricted access across all schools and departments")
    void testIqacUnrestrictedAccess() {
        authenticateUser(iqacUser);

        // IQAC can access all schools
        List<School> schools = academicService.getAllSchools();
        assertTrue(schools.size() >= 2);

        // IQAC can access departments across schools
        Department fetchedDeptA = academicService.getDepartmentById(deptA1.getId());
        Department fetchedDeptB = academicService.getDepartmentById(deptB1.getId());
        assertNotNull(fetchedDeptA);
        assertNotNull(fetchedDeptB);

        // IQAC can access programmes across schools
        MasterProgramme fetchedProgA = academicService.getProgrammeById(progA1.getId());
        MasterProgramme fetchedProgB = academicService.getProgrammeById(progB1.getId());
        assertNotNull(fetchedProgA);
        assertNotNull(fetchedProgB);

        // IQAC can access batches across schools
        ProgrammeBatch fetchedBatchA = academicService.getBatchById(batchA1.getId());
        ProgrammeBatch fetchedBatchB = academicService.getBatchById(batchB1.getId());
        assertNotNull(fetchedBatchA);
        assertNotNull(fetchedBatchB);

        // IQAC can access courses across schools
        ProgrammeBatchCourse fetchedCourseA = academicService.getCourseById(courseA1.getId());
        ProgrammeBatchCourse fetchedCourseB = academicService.getCourseById(courseB1.getId());
        assertNotNull(fetchedCourseA);
        assertNotNull(fetchedCourseB);
    }
}
