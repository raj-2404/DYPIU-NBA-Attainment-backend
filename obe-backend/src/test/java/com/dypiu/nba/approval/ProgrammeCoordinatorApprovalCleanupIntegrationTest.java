package com.dypiu.nba.approval;

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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ProgrammeCoordinatorApprovalCleanupIntegrationTest {

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
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String schoolId;
    private String deptId;
    private String progId1;
    private String progId2;
    private String batchId1;
    private String batchId2;
    private String courseId1;
    private String pbcId1;
    private String pbcId2;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        schoolId = "sch-" + uid;
        deptId = "dept-" + uid;
        progId1 = "prog1-" + uid;
        progId2 = "prog2-" + uid;
        batchId1 = "batch1-" + uid;
        batchId2 = "batch2-" + uid;
        courseId1 = "crs1-" + uid;
        pbcId1 = "pbc1-" + uid;
        pbcId2 = "pbc2-" + uid;

        School school = schoolRepository.save(School.builder().id(schoolId).name("School of Tech").code("SOT-" + uid).build());
        Department dept = departmentRepository.save(Department.builder().id(deptId).schoolId(schoolId).name("Computer Dept").code("CS-" + uid).status("ACTIVE").build());

        MasterProgramme prog1 = masterProgrammeRepository.save(MasterProgramme.builder().id(progId1).departmentId(deptId).departmentName(dept.getName()).name("B.Tech CSE").code("BCSE-" + uid).durationYears(4).status("ACTIVE").build());
        MasterProgramme prog2 = masterProgrammeRepository.save(MasterProgramme.builder().id(progId2).departmentId(deptId).departmentName(dept.getName()).name("B.Tech IT").code("BIT-" + uid).durationYears(4).status("ACTIVE").build());

        ProgrammeBatch batch1 = programmeBatchRepository.save(ProgrammeBatch.builder().id(batchId1).masterProgrammeId(progId1).name("2026-2030 Batch").startYear(2026).endYear(2030).status("ACTIVE").coordinatorEmail("pc_clean@dypiu.ac.in").build());
        ProgrammeBatch batch2 = programmeBatchRepository.save(ProgrammeBatch.builder().id(batchId2).masterProgrammeId(progId2).name("2026-2030 IT Batch").startYear(2026).endYear(2030).status("ACTIVE").coordinatorEmail("other_pc@dypiu.ac.in").build());

        

        ProgrammeBatchCourse pbc1 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().id(pbcId1).programmeBatchId(batchId1).masterCourseId(courseId1).semester(5).courseCodeOverride("CS301").courseNameOverride("Compiler Design").courseCoordinatorName("Dr. Turing").courseCoordinatorId(101L).status("ACTIVE").build());

        ProgrammeBatchCourse pbc2 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().id(pbcId2).programmeBatchId(batchId2).masterCourseId(courseId1).semester(5).courseCodeOverride("IT301").courseNameOverride("Compiler IT").courseCoordinatorName("Dr. Knuth").courseCoordinatorId(102L).status("ACTIVE").build());

        // Create PC User with NULL masterProgrammeId to strictly verify batch-scope resolution
        userRepository.save(User.builder()
                .username("pc_clean")
                .email("pc_clean@dypiu.ac.in")
                .passwordHash("password")
                .name("PC Clean")
                .role(UserRole.PROGRAMME_COORDINATOR)
                .schoolId(schoolId)
                .departmentId(deptId)
                .masterProgrammeId(null)
                .isActive(true)
                .build());
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 1: GET /api/v1/academic/programme-batches resolves assigned batches when masterProgrammeId is null")
    void testGetAssignedProgrammeBatches() throws Exception {
        mockMvc.perform(get("/api/v1/academic/programme-batches")
                        .param("coordinatorEmail", "pc_clean@dypiu.ac.in"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].programmeBatchId", is(batchId1)))
                .andExpect(jsonPath("$.data[0].coordinatorEmail", is("pc_clean@dypiu.ac.in")));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 2: GET /api/v1/approvals/pending grouped by Programme-Batch-Course")
    void testPendingApprovalsInbox() throws Exception {
        // Create 3 approval requests for pbc1 (batch1)
        ApprovalRequest req1 = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-att-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ApprovalType.ATTAINMENT_CONFIGURATION)
                .title("Attainment Settings")
                .resourceId("att-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now().minusHours(2))
                .build());

        ApprovalRequest req2 = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-co-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ApprovalType.CO_TARGETS)
                .title("Course Outcomes & Targets")
                .resourceId("co-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now().minusHours(1))
                .build());

        ApprovalRequest req3 = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-atr-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ApprovalType.COURSE_ATR)
                .title("Course ATR")
                .resourceId("atr-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/approvals/pending")
                        .param("programmeBatchId", batchId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.programmeBatchId", is(batchId1)))
                .andExpect(jsonPath("$.data.totalPendingItems", is(3)))
                .andExpect(jsonPath("$.data.totalProgrammeBatchCourses", is(1)))
                .andExpect(jsonPath("$.data.courses", hasSize(1)))
                .andExpect(jsonPath("$.data.courses[0].programmeBatchCourseId", is(pbcId1)))
                .andExpect(jsonPath("$.data.courses[0].courseCode", is("CS301")))
                .andExpect(jsonPath("$.data.courses[0].courseName", is("Compiler Design")))
                .andExpect(jsonPath("$.data.courses[0].approvalItems", hasSize(3)))
                .andExpect(jsonPath("$.data.courses[0].approvalItems[0].type", isIn(List.of("ATTAINMENT_SETTINGS", "COURSE_OUTCOMES_TARGETS", "COURSE_ATR"))))
                .andExpect(jsonPath("$.data.courses[0].approvalItems[0].status", is("PENDING")))
                .andExpect(jsonPath("$.data.courses[0].approvalItems[0].approvalRequestId", notNullValue()));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 3: GET /api/v1/approvals/reviewed with Approved and Revision Requested states")
    void testReviewedApprovalsInbox() throws Exception {
        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-rev-1")
                .type(ApprovalType.ATTAINMENT_SETTINGS)
                .title("Attainment Settings")
                .resourceId("att-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.APPROVED)
                .submittedBy("cc1@dypiu.ac.in")
                .approvedBy("PC Clean")
                .approvedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/approvals/reviewed")
                        .param("programmeBatchId", batchId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalReviewedItems", is(1)))
                .andExpect(jsonPath("$.data.courses[0].approvalItems[0].status", is("APPROVED")))
                .andExpect(jsonPath("$.data.courses[0].approvalItems[0].type", is("ATTAINMENT_SETTINGS")));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 4: GET /api/v1/approvals/programme-batch-courses/{programmeBatchCourseId}")
    void testCourseApprovalWorkspace() throws Exception {
        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-ws-1")
                .type(ApprovalType.ATTAINMENT_CONFIGURATION)
                .title("Attainment Settings")
                .resourceId("att-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/approvals/programme-batch-courses/{programmeBatchCourseId}", pbcId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.programmeBatchCourse.programmeBatchCourseId", is(pbcId1)))
                .andExpect(jsonPath("$.data.programmeBatchCourse.courseCode", is("CS301")))
                .andExpect(jsonPath("$.data.programmeBatchCourse.semester", is(5)))
                .andExpect(jsonPath("$.data.approvalItems", hasSize(1)))
                .andExpect(jsonPath("$.data.approvalItems[0].approvalRequestId", is("app-ws-1")))
                .andExpect(jsonPath("$.data.approvalItems[0].type", is("ATTAINMENT_SETTINGS")))
                .andExpect(jsonPath("$.data.approvalItems[0].status", is("PENDING")));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 5: GET /api/v1/attainment/configurations/programme-batch-courses/{programmeBatchCourseId}")
    void testGetAttainmentConfig() throws Exception {
        mockMvc.perform(get("/api/v1/attainment/configurations/programme-batch-courses/{id}", pbcId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 6: GET /api/v1/academic/course-outcomes?programmeBatchCourseId=...")
    void testGetCourseOutcomes() throws Exception {
        mockMvc.perform(get("/api/v1/academic/course-outcomes")
                        .param("programmeBatchCourseId", pbcId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 7: GET /api/v1/atr/course/{programmeBatchCourseId}")
    void testGetCourseAtr() throws Exception {
        mockMvc.perform(get("/api/v1/atr/course/{id}", pbcId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 8: POST /api/v1/approvals/{approvalRequestId}/approve")
    void testApproveRequest() throws Exception {
        ApprovalRequest req = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-act-1")
                .type(ApprovalType.COURSE_OUTCOMES_TARGETS)
                .title("Course Outcomes & Targets")
                .resourceId("co-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", "app-act-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.approvalRequestId", is("app-act-1")))
                .andExpect(jsonPath("$.data.type", is("COURSE_OUTCOMES_TARGETS")))
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.reviewedBy.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify Endpoint 9: POST /api/v1/approvals/{approvalRequestId}/request-revision")
    void testRequestRevision() throws Exception {
        ApprovalRequest req = approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-act-2")
                .type(ApprovalType.COURSE_ATR)
                .title("Course ATR")
                .resourceId("atr-" + pbcId1)
                .programmeBatchId(batchId1)
                .programmeBatchCourseId(pbcId1)
                .masterProgrammeId(progId1)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc1@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/approvals/{id}/request-revision", "app-act-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Please provide detailed observations for CO2.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.approvalRequestId", is("app-act-2")))
                .andExpect(jsonPath("$.data.type", is("COURSE_ATR")))
                .andExpect(jsonPath("$.data.status", is("REVISION_REQUESTED")))
                .andExpect(jsonPath("$.data.revisionReason", is("Please provide detailed observations for CO2.")));
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Security Scope: Programme Coordinator cannot access unauthorized batch under another coordinator (HTTP 403)")
    void testUnauthorizedBatchAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/approvals/pending")
                        .param("programmeBatchId", batchId2))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Security Scope: Programme Coordinator cannot access unauthorized course workspace (HTTP 403)")
    void testUnauthorizedCourseWorkspaceForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/approvals/programme-batch-courses/{id}", pbcId2))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Security Scope: Approving an unauthorized approval request returns HTTP 403")
    void testApproveUnauthorizedRequestForbidden() throws Exception {
        approvalRequestRepository.save(ApprovalRequest.builder()
                .id("app-unauth-1")
                .type(ApprovalType.COURSE_OUTCOMES_TARGETS)
                .title("Unauthorized Course Outcomes")
                .resourceId("co-" + pbcId2)
                .programmeBatchId(batchId2)
                .programmeBatchCourseId(pbcId2)
                .masterProgrammeId(progId2)
                .status(ApprovalStatus.PENDING)
                .submittedBy("cc2@dypiu.ac.in")
                .submittedAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", "app-unauth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Security Scope: Non-existent approval request returns HTTP 404")
    void testNonExistentApprovalNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/approvals/{id}/approve", "non-existent-app-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "pc_clean", roles = {"PROGRAMME_COORDINATOR"})
    @DisplayName("Verify nested competency array-of-arrays in poKeywordsStore and psoKeywordsStore")
    void testSaveAndRetrieveNestedCompetencyKeywords() throws Exception {
        String payload = """
        {
          "poMappings": [
            { "courseOutcomeId": "co-clean-1", "poCode": "PO1", "mappingLevel": 3 },
            { "courseOutcomeId": "co-clean-1", "poCode": "PO2", "mappingLevel": 2 }
          ],
          "psoMappings": [
            { "courseOutcomeId": "co-clean-1", "psoCode": "PSO1", "mappingLevel": 3 }
          ],
          "poKeywordsStore": {
            "C321.1": {
              "PO1": [["qww"], ["wqw"], ["ddsdddddd"]],
              "PO2": [["w", "ww"]]
            },
            "C321.2": {
              "PO1": [["ww", "dsd"]],
              "PO2": [["wq", "www"]]
            },
            "C321.5": {
              "PO1": [[], []],
              "PO2": [["wwqw"], ["wqwwwq"]]
            }
          },
          "psoKeywordsStore": {
            "C321.1": {
              "PSO1": [["wqww"]]
            }
          }
        }
        """;

        mockMvc.perform(put("/programme-batch-courses/{id}/co-po-pso-mappings", pbcId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][0][0]", is("qww")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][1][0]", is("wqw")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][2][0]", is("ddsdddddd")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO2'][0][0]", is("w")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO2'][0][1]", is("ww")));

        // Verify retrieval via GET
        mockMvc.perform(get("/programme-batch-courses/{id}/co-po-pso-mappings", pbcId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][0][0]", is("qww")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][1][0]", is("wqw")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO1'][2][0]", is("ddsdddddd")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO2'][0][0]", is("w")))
                .andExpect(jsonPath("$.data.poKeywordsStore['C321.1']['PO2'][0][1]", is("ww")));
    }
}
