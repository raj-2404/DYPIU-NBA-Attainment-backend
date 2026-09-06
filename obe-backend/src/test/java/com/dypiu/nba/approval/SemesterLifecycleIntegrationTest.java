package com.dypiu.nba.approval;

import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.dto.SemesterReadinessDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.ApprovalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SemesterLifecycleIntegrationTest {

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
    private MasterCourseRepository masterCourseRepository;

    @Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ObjectMapper objectMapper;

    private String schoolId;
    private String deptId;
    private String progId;
    private String batchId;
    private String courseSem1Id;
    private String courseSem2Id;
    private String pbcSem1Id;
    private String pbcSem2Id;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        schoolId = "sch-" + uid;
        deptId = "dept-" + uid;
        progId = "prog-" + uid;
        batchId = "batch-" + uid;
        courseSem1Id = "crs1-" + uid;
        courseSem2Id = "crs2-" + uid;
        pbcSem1Id = "pbc1-" + uid;
        pbcSem2Id = "pbc2-" + uid;

        School school = schoolRepository.save(School.builder().id(schoolId).name("Engineering School").code("ENG-" + uid).build());
        Department dept = departmentRepository.save(Department.builder().id(deptId).schoolId(schoolId).name("Computer Science").code("CS-" + uid).status("ACTIVE").hodEmail("hod_sem@dypiu.ac.in").build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id(progId)
                .departmentId(deptId)
                .departmentName(dept.getName())
                .name("B.Tech CSE")
                .code("BCSE-" + uid)
                .durationYears(4)
                .status("ACTIVE")
                .coordinatorEmail("pc_sem@dypiu.ac.in")
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id(batchId)
                .masterProgrammeId(progId)
                .name("2026-2030 Batch")
                .startYear(2026)
                .endYear(2030)
                .durationYears(4)
                .status("ACTIVE")
                .coordinatorEmail("pc_sem@dypiu.ac.in")
                .build());

        MasterCourse mc1 = masterCourseRepository.save(MasterCourse.builder()
                .id(courseSem1Id)
                .masterProgrammeId(progId)
                .code("CS101")
                .name("Programming in C")
                .credits(4)
                .semester("1")
                .courseType("CORE")
                .status("ACTIVE")
                .build());

        MasterCourse mc2 = masterCourseRepository.save(MasterCourse.builder()
                .id(courseSem2Id)
                .masterProgrammeId(progId)
                .code("CS201")
                .name("Data Structures")
                .credits(4)
                .semester("2")
                .courseType("CORE")
                .status("ACTIVE")
                .build());

        ProgrammeBatchCourse pbc1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id(pbcSem1Id)
                .programmeBatchId(batchId)
                .masterCourseId(courseSem1Id)
                .semester(1)
                .code("CS101")
                .name("Programming in C")
                .courseCoordinatorName("Dr. Ada Lovelace")
                .assignedFaculty("Dr. Ada Lovelace (cc1@dypiu.ac.in)")
                .status("ACTIVE")
                .build());

        ProgrammeBatchCourse pbc2 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id(pbcSem2Id)
                .programmeBatchId(batchId)
                .masterCourseId(courseSem2Id)
                .semester(2)
                .code("CS201")
                .name("Data Structures")
                .courseCoordinatorName("Dr. Charles Babbage")
                .assignedFaculty("Dr. Charles Babbage (cc2@dypiu.ac.in)")
                .status("ACTIVE")
                .build());

        userRepository.save(User.builder().email("pc_sem@dypiu.ac.in").name("PC Sem").username("pc_sem").passwordHash("hash").role(UserRole.PROGRAMME_COORDINATOR).masterProgrammeId(progId).departmentId(deptId).schoolId(schoolId).isActive(true).build());
        userRepository.save(User.builder().email("hod_sem@dypiu.ac.in").name("HOD Sem").username("hod_sem").passwordHash("hash").role(UserRole.HOD).departmentId(deptId).schoolId(schoolId).isActive(true).build());
        userRepository.save(User.builder().email("cc1@dypiu.ac.in").name("CC1").username("cc1").passwordHash("hash").role(UserRole.FACULTY).departmentId(deptId).schoolId(schoolId).isActive(true).build());
        userRepository.save(User.builder().email("cc2@dypiu.ac.in").name("CC2").username("cc2").passwordHash("hash").role(UserRole.FACULTY).departmentId(deptId).schoolId(schoolId).isActive(true).build());
    }

    @Test
    @DisplayName("Semester allocation submission creates scoped approval and HOD approval isolates to that semester")
    @WithMockUser(username = "pc_sem@dypiu.ac.in", roles = {"PROGRAMME_COORDINATOR"})
    void testSemesterScopedAllocationAndHodApproval() throws Exception {
        Map<String, Object> allocSem1 = Map.of(
                "masterCourseId", courseSem1Id,
                "semester", 1,
                "coordinator", "Dr. Ada Lovelace",
                "coordinatorEmail", "cc1@dypiu.ac.in"
        );

        Map<String, Object> bodySem1 = Map.of(
                "masterProgrammeId", progId,
                "programmeBatchId", batchId,
                "allocations", List.of(allocSem1),
                "submit", true
        );

        // 1. PC submits Sem 1 allocation
        mockMvc.perform(post("/api/v1/academic/master-courses/allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodySem1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String sem1Key = "allocation-" + batchId + "-sem-1";
        String sem2Key = "allocation-" + batchId + "-sem-2";

        // Verify Sem 1 approval request was created
        assertTrue(approvalRequestRepository.findAll().stream().anyMatch(a -> sem1Key.equalsIgnoreCase(a.getResourceId())));

        // 2. HOD approves Sem 1 allocation
        approvalService.verifyStatus(sem1Key, "allocationStatus", "APPROVED", "Sem 1 allocations approved", "hod_sem@dypiu.ac.in");

        assertTrue(approvalService.isAllocationApproved(batchId, 1));
        assertFalse(approvalService.isAllocationApproved(batchId, 2));

        // 3. PC can independently submit Sem 2 allocation without touching Sem 1
        Map<String, Object> allocSem2 = Map.of(
                "masterCourseId", courseSem2Id,
                "semester", 2,
                "coordinator", "Dr. Charles Babbage",
                "coordinatorEmail", "cc2@dypiu.ac.in"
        );
        Map<String, Object> bodySem2 = Map.of(
                "masterProgrammeId", progId,
                "programmeBatchId", batchId,
                "allocations", List.of(allocSem2),
                "submit", true
        );

        mockMvc.perform(post("/api/v1/academic/master-courses/allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodySem2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(approvalRequestRepository.findAll().stream().anyMatch(a -> sem2Key.equalsIgnoreCase(a.getResourceId())));
    }

    @Test
    @DisplayName("Semester Readiness Overview endpoint returns warning list and canComplete=true")
    @WithMockUser(username = "hod_sem@dypiu.ac.in", roles = {"HOD"})
    void testSemesterReadinessOverviewAndWarnings() throws Exception {
        mockMvc.perform(get("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.programmeBatchId").value(batchId))
                .andExpect(jsonPath("$.data.semester").value(1))
                .andExpect(jsonPath("$.data.isCompleted").value(false))
                .andExpect(jsonPath("$.data.canComplete").value(true))
                .andExpect(jsonPath("$.data.courseCount").value(1))
                .andExpect(jsonPath("$.data.warnings", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("HOD can complete semester, freezing offerings, and audited reopening restores editability")
    @WithMockUser(username = "hod_sem@dypiu.ac.in", roles = {"HOD"})
    void testSemesterCompleteAndReopenWorkflow() throws Exception {
        // 1. Complete Semester 1 by HOD
        mockMvc.perform(post("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "End of Semester 1 examinations"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Verify pbc1 is marked COMPLETED
        ProgrammeBatchCourse pbc1 = programmeBatchCourseRepository.findById(pbcSem1Id).orElseThrow();
        assertEquals("COMPLETED", pbc1.getStatus());

        // Verify pbc2 remains ACTIVE
        ProgrammeBatchCourse pbc2 = programmeBatchCourseRepository.findById(pbcSem2Id).orElseThrow();
        assertEquals("ACTIVE", pbc2.getStatus());

        // 2. Reopening requires reason
        mockMvc.perform(post("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());

        // 3. Reopen with valid reason by HOD
        mockMvc.perform(post("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Recalculating moderation marks for course CS101"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NOT_COMPLETED"));

        // Verify pbc1 is restored to ACTIVE
        pbc1 = programmeBatchCourseRepository.findById(pbcSem1Id).orElseThrow();
        assertEquals("ACTIVE", pbc1.getStatus());
    }

    @Test
    @DisplayName("Non-HOD cannot complete or reopen semester")
    @WithMockUser(username = "pc_sem@dypiu.ac.in", roles = {"PROGRAMME_COORDINATOR"})
    void testNonHodForbiddenFromSemesterLifecycleActions() throws Exception {
        mockMvc.perform(post("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Attempt by PC"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/academic/programme-batches/" + batchId + "/semesters/1/reopen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Attempt by PC"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Semester status overview lists all semesters with their status")
    @WithMockUser(username = "pc_sem@dypiu.ac.in", roles = {"PROGRAMME_COORDINATOR"})
    void testSemestersStatusOverview() throws Exception {
        mockMvc.perform(get("/api/v1/academic/programme-batches/" + batchId + "/semesters/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(8))) // 4 years * 2 = 8 semesters
                .andExpect(jsonPath("$.data[0].semester").value(1))
                .andExpect(jsonPath("$.data[0].isCompleted").value(false));
    }
}
