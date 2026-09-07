package com.dypiu.nba.security;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CourseCoordinatorExaminationAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private User coordinatorUser;
    private User otherCoordinatorUser;
    private ProgrammeBatchCourse offeringWithId;
    private ProgrammeBatchCourse offeringWithEmail;
    private ProgrammeBatchCourse offeringWithName;
    private ProgrammeBatchCourse offeringWithEmailInName;
    private ProgrammeBatchCourse offeringWithAssignedFacultyOnly;
    private ProgrammeBatchCourse offeringUnassigned;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        School school = schoolRepository.save(School.builder()
                .id("sch-test-" + uid)
                .name("School of Engineering")
                .code("SOE-" + uid)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-test-" + uid)
                .schoolId(school.getId())
                .name("Computer Science")
                .code("CS-" + uid)
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-test-" + uid)
                .departmentId(dept.getId())
                .name("B.Tech CSE")
                .code("BCSE-" + uid)
                .build());

        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-alloc-" + uid)
                .type(ApprovalType.COURSE_ALLOCATION)
                .title("Course Allocation for " + prog.getId())
                .masterProgrammeId(prog.getId())
                .resourceId("allocation-" + prog.getId())
                .status(ApprovalStatus.APPROVED)
                .submittedBy("admin")
                .approvedBy("HOD")
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-test-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Batch 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .build());

        ProgrammeBatchCourse course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("crs-exam-101")
                .programmeBatchId(batch.getId())
                .code("CS301")
                .name("Database Systems")
                .credits(4)
                .courseType("THEORY")
                .semester(5)
                .status("ACTIVE")
                .build());

        coordinatorUser = userRepository.save(User.builder()
                .username("coord_" + uid)
                .email("coordinator." + uid + "@dypiu.ac.in")
                .passwordHash("password")
                .name("Dr. Coordinator Test")
                .role(UserRole.FACULTY)
                .schoolId(school.getId())
                .departmentId(dept.getId())
                .isActive(true)
                .build());

        otherCoordinatorUser = userRepository.save(User.builder()
                .username("other_coord_" + uid)
                .email("other.coord." + uid + "@dypiu.ac.in")
                .passwordHash("password")
                .name("Dr. Unrelated Coordinator")
                .role(UserRole.FACULTY)
                .schoolId(school.getId())
                .departmentId(dept.getId())
                .isActive(true)
                .build());

        // 1. Matched by ID
        offeringWithId = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-id-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(1)
                .courseCoordinatorId(coordinatorUser.getId())
                .build());

        // 2. Matched by coordinatorEmail
        offeringWithEmail = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-email-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(2)
                .build());
        offeringWithEmail.setCourseCoordinatorEmail(coordinatorUser.getEmail());

        // 3. Matched by coordinatorName
        offeringWithName = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-name-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(3)
                .courseCoordinatorName(coordinatorUser.getName())
                .build());

        // 4. Matched by email stored in coordinatorName (legacy)
        offeringWithEmailInName = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-emailinname-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(4)
                .courseCoordinatorName(coordinatorUser.getEmail().toUpperCase())
                .build());

        // 5. Unassigned / null coordinator
        offeringUnassigned = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-none-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(5)
                .build());

        // 6. Runtime pattern (offering-8867ab03): coordinator ID/name null, assignedFaculty = email
        offeringWithAssignedFacultyOnly = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-8867ab03-" + uid)
                
                .programmeBatchId(batch.getId())
                .semester(6)
                .assignedFaculty(coordinatorUser.getEmail())
                .build());
    }

    @Test
    @DisplayName("Matching coordinator ID allowed")
    void testMatchingCoordinatorIdAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithId.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Matching coordinatorEmail allowed")
    void testMatchingCoordinatorEmailAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithEmail.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Matching coordinatorName allowed")
    void testMatchingCoordinatorNameAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithName.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Email stored in coordinatorName allowed")
    void testEmailStoredInCoordinatorNameAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithEmailInName.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Runtime pattern: offering with assignedFaculty matching coordinator email allowed")
    void testRuntimeAssignedFacultyPatternAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithAssignedFacultyOnly.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unrelated coordinator denied with 403")
    void testUnrelatedCoordinatorDeniedWith403() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithId.getId())
                        .with(user(otherCoordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Null/unassigned coordinator denied with 403")
    void testNullUnassignedCoordinatorDeniedWith403() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringUnassigned.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("All HTTP methods (GET, POST JSON, DELETE) succeed for assigned coordinator")
    void testAllHttpMethodsSucceedForAssignedCoordinator() throws Exception {
        String jsonPayload = """
        {
          "thresholdPercentage": 60.0,
          "coMaxMarks": {"CO1": 20.0},
          "students": []
        }
        """;

        // 1. GET examination
        mockMvc.perform(get("/api/v1/attainment/examination/{id}", offeringWithAssignedFacultyOnly.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());

        // 2. POST JSON examination save
        mockMvc.perform(post("/api/v1/attainment/examination/{id}", offeringWithAssignedFacultyOnly.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());

        // 3. DELETE examination data
        mockMvc.perform(delete("/api/v1/attainment/examination/{id}", offeringWithAssignedFacultyOnly.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("All Survey HTTP methods (GET, POST JSON, DELETE) succeed for assigned coordinator")
    void testAllSurveyHttpMethodsSucceedForAssignedCoordinator() throws Exception {
        String surveyJsonPayload = """
        {
          "thresholdPercentage": 60.0,
          "coThresholdPercentages": {"CO1": 60.0},
          "surveyResponses": []
        }
        """;

        // 1. GET survey
        mockMvc.perform(get("/api/v1/attainment/survey/{id}", offeringWithAssignedFacultyOnly.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());

        // 2. POST JSON survey save
        mockMvc.perform(post("/api/v1/attainment/survey/{id}", offeringWithAssignedFacultyOnly.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(surveyJsonPayload)
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());

        // 3. DELETE survey data
        mockMvc.perform(delete("/api/v1/attainment/survey/{id}", offeringWithAssignedFacultyOnly.getId())
                        .with(user(coordinatorUser.getEmail()).roles("FACULTY")))
                .andExpect(status().isOk());
    }
}
