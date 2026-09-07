package com.dypiu.nba.reports;

import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.reports.model.*;
import com.dypiu.nba.reports.repository.ReportArtifactRepository;
import com.dypiu.nba.reports.repository.ReportAssetRepository;
import com.dypiu.nba.reports.repository.ReportRepository;
import com.dypiu.nba.reports.repository.ReportTemplateRepository;
import com.dypiu.nba.reports.service.ReportOrchestrationService;
import com.dypiu.nba.reports.template.HeaderConfig;
import com.dypiu.nba.reports.template.ReportTemplateDto;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class Phase55ReportManagementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportOrchestrationService orchestrationService;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportArtifactRepository artifactRepository;

    @Autowired
    private ReportAssetRepository assetRepository;

    @Autowired
    private ReportTemplateRepository templateRepository;

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
    private CourseOutcomeRepository courseOutcomeRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private CurrentUserScopeService currentUserScopeService;

    private School school;
    private Department dept;
    private MasterProgramme prog;
    private ProgrammeBatch batch;
    private ProgrammeBatchCourse course;
    private ProgrammeBatchCourse offering;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        school = schoolRepository.save(School.builder()
                .id("sch-" + uid)
                .code("SOET-" + uid)
                .name("School of Engineering and Technology")
                .build());

        dept = departmentRepository.save(Department.builder()
                .id("dept-" + uid)
                .schoolId(school.getId())
                .code("CSE-" + uid)
                .name("Department of Computer Science & Engineering")
                .build());

        prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-" + uid)
                .departmentId(dept.getId())
                .code("BTECH-CSE-" + uid)
                .name("Bachelor of Technology in Computer Science")
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-" + uid)
                .masterProgrammeId(prog.getId())
                .programmeName("B.Tech CSE")
                .name("B.Tech CSE 2021-2025")
                .startYear(2021)
                .endYear(2025)
                .build());

        course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("mc-" + uid)
                .masterProgrammeId(prog.getId())
                .code("CS401-" + uid)
                .name("Distributed Systems & Cloud Computing")
                .credits(4)
                .build());

        offering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-" + uid)
                .masterCourseId(course.getId())
                .programmeBatchId(batch.getId())
                .semester(7)
                .courseCoordinatorName("Dr. Alice Sharma")
                .build());

        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co1-" + uid)
                .programmeBatchCourseId(offering.getId())
                .code("CO1")
                .statement("Understand distributed computing concepts")
                .targetLevel(new BigDecimal("2.5"))
                .build());
    }

    // =========================================================================
    // 1. TEMPLATE MANAGEMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("IQAC can retrieve default/current institutional report template")
    @WithMockUser(username = "iqac_user", roles = {"IQAC"})
    void testGetInstitutionalTemplate() throws Exception {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        mockMvc.perform(get("/api/v1/reports/template")
                        .param("institutionId", "DYPIU")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.institutionId").value("DYPIU"))
                .andExpect(jsonPath("$.data.headerConfig.institutionName").value("D. Y. PATIL INTERNATIONAL UNIVERSITY, PUNE"))
                .andExpect(jsonPath("$.data.headerConfig.showLogo").value(true));
    }

    @Test
    @DisplayName("IQAC can update institutional report template and header configuration")
    @WithMockUser(username = "iqac_user", roles = {"IQAC"})
    void testUpdateInstitutionalTemplate() throws Exception {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        HeaderConfig updatedHeader = HeaderConfig.builder()
                .institutionName("DYPIU ADVANCED CAMPUS")
                .subHeader("Sector 29, Akurdi, Pune")
                .accreditationText("Accredited by NBA & NAAC Grade A++")
                .headerTitle("AUTHORITATIVE REPORT")
                .showLogo(true)
                .build();

        ReportTemplateDto templateDto = ReportTemplateDto.builder()
                .templateName("DYPIU Institutional Master Template")
                .institutionId("DYPIU")
                .headerConfig(updatedHeader)
                .build();

        mockMvc.perform(put("/api/v1/reports/template")
                        .param("institutionId", "DYPIU")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.headerConfig.institutionName").value("DYPIU ADVANCED CAMPUS"))
                .andExpect(jsonPath("$.data.headerConfig.accreditationText").value("Accredited by NBA & NAAC Grade A++"));

        // Verify HeaderConfig shortcut endpoint
        mockMvc.perform(get("/api/v1/reports/template/header")
                        .param("institutionId", "DYPIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.institutionName").value("DYPIU ADVANCED CAMPUS"));
    }

    @Test
    @DisplayName("Faculty role is FORBIDDEN from updating report template")
    @WithMockUser(username = "faculty_user", roles = {"FACULTY"})
    void testFacultyCannotUpdateTemplate() throws Exception {
        CurrentUserScope facultyScope = CurrentUserScope.builder()
                .role(UserRole.FACULTY)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(facultyScope);

        HeaderConfig headerConfig = HeaderConfig.builder()
                .institutionName("Hacked University")
                .build();

        mockMvc.perform(put("/api/v1/reports/template/header")
                        .param("institutionId", "DYPIU")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(headerConfig)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. ASSET & LOGO MANAGEMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("IQAC can upload Left Logo and Right Logo, verify metadata, view raw, and delete")
    @WithMockUser(username = "iqac_user", roles = {"IQAC"})
    void testLogoUploadViewAndDeleteLifecycle() throws Exception {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // 1. Upload Left Logo
        byte[] dummyPng = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        MockMultipartFile leftLogoFile = new MockMultipartFile(
                "file", "university_left_logo.png", "image/png", dummyPng);

        String uploadResponseStr = mockMvc.perform(multipart("/api/v1/reports/assets/upload")
                        .file(leftLogoFile)
                        .param("assetType", "LEFT_LOGO")
                        .param("institutionId", "DYPIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetType").value("LEFT_LOGO"))
                .andExpect(jsonPath("$.data.originalFilename").value("university_left_logo.png"))
                .andReturn().getResponse().getContentAsString();

        ApiResponse<?> uploadResponse = objectMapper.readValue(uploadResponseStr, ApiResponse.class);
        String assetId = ((java.util.Map<?, ?>) uploadResponse.getData()).get("assetId").toString();
        assertNotNull(assetId);

        // 2. Verify Template HeaderConfig now references left logo assetId
        mockMvc.perform(get("/api/v1/reports/template/header")
                        .param("institutionId", "DYPIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leftLogoAssetId").value(assetId));

        // 3. View / Raw download of asset
        mockMvc.perform(get("/api/v1/reports/assets/" + assetId + "/raw"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        // 4. List assets
        mockMvc.perform(get("/api/v1/reports/assets")
                        .param("institutionId", "DYPIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // 5. Delete asset and verify unlinking from HeaderConfig
        mockMvc.perform(delete("/api/v1/reports/assets/" + assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/reports/template/header")
                        .param("institutionId", "DYPIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leftLogoAssetId").value(nullValue()));
    }

    @Test
    @DisplayName("Invalid file upload is rejected with Bad Request")
    @WithMockUser(username = "iqac_user", roles = {"IQAC"})
    void testInvalidFileUploadRejected() throws Exception {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/v1/reports/assets/upload")
                        .file(emptyFile)
                        .param("assetType", "LEFT_LOGO"))
                .andExpect(status().isBadRequest());

        // Invalid extension
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malicious.exe", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/reports/assets/upload")
                        .file(exeFile)
                        .param("assetType", "LEFT_LOGO"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. GENERATED REPORTS LISTING & DETAILS TESTS
    // =========================================================================

    @Test
    @DisplayName("Generated reports listing returns persisted report history with artifacts")
    @WithMockUser(username = "iqac_user", roles = {"IQAC"})
    void testListGeneratedReportsAndDetails() throws Exception {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Generate a report first
        GeneratedReportDto generated = orchestrationService.generateCourseAttainmentReport(
                offering.getId(), "Dr. Alice", "DYPIU");

        assertNotNull(generated.getReportId());
        assertFalse(generated.getArtifacts().isEmpty());

        // 1. List Generated Reports
        mockMvc.perform(get("/api/v1/reports")
                        .param("institutionId", "DYPIU")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].reportId").value(generated.getReportId()))
                .andExpect(jsonPath("$.data[0].reportType").value("COURSE_ATTAINMENT"))
                .andExpect(jsonPath("$.data[0].artifacts", hasSize(2)));

        // 2. Get Report by ID
        mockMvc.perform(get("/api/v1/reports/" + generated.getReportId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(generated.getReportId()))
                .andExpect(jsonPath("$.data.generatedBy").value("Dr. Alice"));

        // 3. Download persisted artifact using artifactId
        String pdfArtifactId = generated.getArtifacts().stream()
                .filter(a -> a.getArtifactType() == ArtifactType.PDF)
                .findFirst().orElseThrow().getArtifactId();

        mockMvc.perform(get("/api/v1/reports/artifacts/" + pdfArtifactId + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().exists("Content-Disposition"));
    }
}
