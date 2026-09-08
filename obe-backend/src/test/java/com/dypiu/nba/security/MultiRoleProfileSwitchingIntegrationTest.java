package com.dypiu.nba.security;

import com.dypiu.nba.dto.AuthResponse;
import com.dypiu.nba.dto.SwitchRoleRequestDto;
import com.dypiu.nba.dto.UserRolesResponseDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class MultiRoleProfileSwitchingIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

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
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private School school;
    private Department department;
    private MasterProgramme programme;
    private ProgrammeBatch batch;

    @BeforeEach
    void setUp() {
        schoolRepository.deleteAll();
        departmentRepository.deleteAll();
        masterProgrammeRepository.deleteAll();
        programmeBatchRepository.deleteAll();
        programmeBatchCourseRepository.deleteAll();
        userRepository.deleteAll();

        school = schoolRepository.save(School.builder()
                .id("school-eng-test")
                .name("School of Engineering")
                .code("SOE")
                .build());

        department = departmentRepository.save(Department.builder()
                .id("dept-cse-test")
                .schoolId(school.getId())
                .name("Computer Science & Engineering")
                .code("CSE")
                .hodEmail("multirole.user@dypiu.ac.in")
                .hod("Prof MultiRole")
                .build());

        programme = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-btech-cse")
                .departmentId(department.getId())
                .departmentName(department.getName())
                .name("B.Tech Computer Science")
                .level("UG")
                .degreeAwarded("B.Tech")
                .durationYears(4)
                .coordinatorEmail("multirole.user@dypiu.ac.in")
                .coordinator("Prof MultiRole")
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-test")
                .masterProgrammeId(programme.getId())
                .name("B.Tech CSE 2024-2028")
                .startYear(2024)
                .endYear(2028)
                .durationYears(4)
                .coordinatorEmail("multirole.user@dypiu.ac.in")
                .coordinatorName("Prof MultiRole")
                .build());

        testUser = userRepository.save(User.builder()
                .username("prof_multirole")
                .email("multirole.user@dypiu.ac.in")
                .name("Prof MultiRole")
                .passwordHash("hashed")
                .role(UserRole.HOD)
                .schoolId(school.getId())
                .departmentId(department.getId())
                .isActive(true)
                .build());

        programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-course-101")
                .programmeBatchId(batch.getId())
                .code("CS101")
                .name("Data Structures")
                .semester(1)
                .credits(4)
                .courseType("CORE")
                .courseCoordinatorId(testUser.getId())
                .courseCoordinatorName(testUser.getName())
                .assignedFaculty(testUser.getEmail())
                .build());
    }

    @Test
    @DisplayName("User with HOD, PC, and Course Coordinator assignments discovers all profiles")
    void testDiscoverAllRoles() {
        Principal principal = () -> testUser.getUsername();
        UserRolesResponseDto response = authService.getAvailableRoles(principal);

        assertNotNull(response);
        assertEquals(testUser.getId(), response.getUserId());
        assertEquals("HOD", response.getPrimaryRole());
        assertNotNull(response.getProfiles());
        assertTrue(response.getProfiles().size() >= 3, "Should discover HOD, PC, and Course Coordinator profiles");

        boolean hasHod = response.getProfiles().stream().anyMatch(p -> "HOD".equals(p.getRole()) && department.getId().equals(p.getDepartmentId()));
        boolean hasPc = response.getProfiles().stream().anyMatch(p -> "PROGRAMME_COORDINATOR".equals(p.getRole()) && batch.getId().equals(p.getProgrammeBatchId()));
        boolean hasCc = response.getProfiles().stream().anyMatch(p -> "COURSE_COORDINATOR".equals(p.getRole()));

        assertTrue(hasHod, "Must include HOD profile for department");
        assertTrue(hasPc, "Must include Programme Coordinator profile for batch");
        assertTrue(hasCc, "Must include Course Coordinator profile with assigned courses");
    }

    @Test
    @DisplayName("Switching role from HOD to COURSE_COORDINATOR issues new JWT with activeRole COURSE_COORDINATOR")
    void testSwitchRoleToCourseCoordinator() {
        Principal principal = () -> testUser.getUsername();
        SwitchRoleRequestDto request = SwitchRoleRequestDto.builder()
                .role("COURSE_COORDINATOR")
                .departmentId(department.getId())
                .schoolId(school.getId())
                .build();

        AuthResponse authResponse = authService.switchRole(request, principal);

        assertNotNull(authResponse);
        assertNotNull(authResponse.getToken());
        assertEquals("COURSE_COORDINATOR", authResponse.getUser().getRole());

        // Validate that token claims contain activeRole
        String activeRole = jwtTokenProvider.getActiveRoleFromJwt(authResponse.getToken());
        assertEquals("COURSE_COORDINATOR", activeRole);
    }

    @Test
    @DisplayName("Switching role back to HOD issues new JWT with activeRole HOD")
    void testSwitchRoleToHod() {
        Principal principal = () -> testUser.getUsername();
        SwitchRoleRequestDto request = SwitchRoleRequestDto.builder()
                .role("HOD")
                .departmentId(department.getId())
                .schoolId(school.getId())
                .build();

        AuthResponse authResponse = authService.switchRole(request, principal);

        assertNotNull(authResponse);
        assertNotNull(authResponse.getToken());
        assertEquals("HOD", authResponse.getUser().getRole());

        String activeRole = jwtTokenProvider.getActiveRoleFromJwt(authResponse.getToken());
        assertEquals("HOD", activeRole);
    }

    @Test
    @DisplayName("Explicit role restriction by IQAC restricts profiles strictly to assigned roles")
    void testExplicitAssignedRolesRestrictsProfiles() {
        // Explicitly set only HOD on user
        testUser.setRoleList(java.util.List.of("HOD"));
        userRepository.save(testUser);

        Principal principal = () -> testUser.getUsername();
        UserRolesResponseDto response = authService.getAvailableRoles(principal);

        assertNotNull(response);
        assertEquals(1, response.getProfiles().size(), "Only HOD role should be available when IQAC explicitly assigns ['HOD']");
        assertEquals("HOD", response.getProfiles().get(0).getRole());

        // Now IQAC assigns both HOD and COURSE_COORDINATOR
        testUser.setRoleList(java.util.List.of("HOD", "COURSE_COORDINATOR"));
        userRepository.save(testUser);

        response = authService.getAvailableRoles(principal);
        assertNotNull(response);
        assertEquals(2, response.getProfiles().size(), "Both HOD and COURSE_COORDINATOR should be available");
        assertTrue(response.getProfiles().stream().anyMatch(p -> "HOD".equals(p.getRole())));
        assertTrue(response.getProfiles().stream().anyMatch(p -> "COURSE_COORDINATOR".equals(p.getRole())));
        assertFalse(response.getProfiles().stream().anyMatch(p -> "PROGRAMME_COORDINATOR".equals(p.getRole())), "PC should not be included");
    }
}
