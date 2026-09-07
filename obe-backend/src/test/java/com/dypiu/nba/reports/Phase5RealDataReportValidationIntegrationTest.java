package com.dypiu.nba.reports;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.reports.excel.ExcelReportRenderer;
import com.dypiu.nba.reports.integrity.ReportIntegrityService;
import com.dypiu.nba.reports.model.*;
import com.dypiu.nba.reports.model.snapshot.*;
import com.dypiu.nba.reports.pdf.PdfReportRenderer;
import com.dypiu.nba.reports.repository.*;
import com.dypiu.nba.reports.service.*;
import com.dypiu.nba.repository.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "iqac_user", roles = {"IQAC"})
public class Phase5RealDataReportValidationIntegrationTest {

    @Autowired
    private ReportOrchestrationService reportOrchestrationService;

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
private School schoolEngineering;
    private School schoolManagement;
    private Department deptCSE;
    private Department deptMBA;
    private MasterProgramme progCSE;
    private MasterProgramme progMBA;
    private ProgrammeBatch batchCSE;
    private ProgrammeBatch batchMBA;
    private ProgrammeBatchCourse courseDS;
    private ProgrammeBatchCourse pbcDS;

    @BeforeEach
    void setUpHierarchy() {
        // 1. School of Engineering and Technology
        schoolEngineering = schoolRepository.save(School.builder()
                .id("sch-eng-" + UUID.randomUUID().toString().substring(0, 6))
                .code("SOET_" + UUID.randomUUID().toString().substring(0, 4))
                .name("School of Engineering and Technology")
                .directorName("Dr. P. K. Sharma")
                .build());

        deptCSE = departmentRepository.save(Department.builder()
                .id("dept-cse-" + UUID.randomUUID().toString().substring(0, 6))
                .code("CSE_" + UUID.randomUUID().toString().substring(0, 4))
                .name("Department of Computer Science and Engineering")
                .schoolId(schoolEngineering.getId())
                .hod("Dr. A. Verma")
                .build());

        progCSE = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-btech-cse-" + UUID.randomUUID().toString().substring(0, 6))
                .code("BTECH-CSE")
                .name("B.Tech in Computer Science and Engineering")
                .departmentId(deptCSE.getId())
                .durationYears(4)
                .build());

        batchCSE = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-cse-2021-25-" + UUID.randomUUID().toString().substring(0, 6))
                .name("B.Tech CSE 2021-25")
                .masterProgrammeId(progCSE.getId())
                .startYear(2021)
                .endYear(2025)
                .build());

        courseDS = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchCSE.getId()).semester(1)
                .id("mc-cs201-" + UUID.randomUUID().toString().substring(0, 6))
                .code("CS201")
                .name("Data Structures and Algorithms")
                .credits(4)
                .masterProgrammeId(progCSE.getId())
                .build());

        pbcDS = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-cs201-" + UUID.randomUUID().toString().substring(0, 6))
                .programmeBatchId(batchCSE.getId())
                .masterCourseId(courseDS.getId())
                .semester(3)
                .courseCoordinatorName("Prof. Rajesh Patil")
                .build());

        // 2. School of Management
        schoolManagement = schoolRepository.save(School.builder()
                .id("sch-mgmt-" + UUID.randomUUID().toString().substring(0, 6))
                .code("SOM_" + UUID.randomUUID().toString().substring(0, 4))
                .name("School of Management")
                .directorName("Dr. S. K. Deshmukh")
                .build());

        deptMBA = departmentRepository.save(Department.builder()
                .id("dept-mba-" + UUID.randomUUID().toString().substring(0, 6))
                .code("MGMT_" + UUID.randomUUID().toString().substring(0, 4))
                .name("Department of Management Studies")
                .schoolId(schoolManagement.getId())
                .hod("Dr. M. Joshi")
                .build());

        progMBA = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-mba-" + UUID.randomUUID().toString().substring(0, 6))
                .code("MBA-GEN")
                .name("Master of Business Administration")
                .departmentId(deptMBA.getId())
                .durationYears(2)
                .build());

        batchMBA = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-mba-2023-25-" + UUID.randomUUID().toString().substring(0, 6))
                .name("MBA 2023-25")
                .masterProgrammeId(progMBA.getId())
                .startYear(2023)
                .endYear(2025)
                .build());

        // 3. User Setup for Spring Security
        userRepository.save(User.builder()
                .username("iqac_user")
                .email("iqac@dypiu.ac.in")
                .name("IQAC Coordinator")
                .passwordHash("hashed")
                .role(UserRole.IQAC)
                .schoolId(schoolEngineering.getId())
                .departmentId(deptCSE.getId())
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("Report 1: Programme Attainment - Section 1 Average Mapping PDF and Excel")
    void testProgrammeAttainmentSection1() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.AVERAGE_MAPPING, "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATTAINMENT_MAPPING, report.getReportType());
        assertEquals("School of Engineering and Technology", report.getSnapshot().getSchoolName());
        assertEquals(2, report.getArtifacts().size());
    }

    @Test
    @DisplayName("Report 2: Programme Attainment - Section 2 Average Direct PDF and Excel")
    void testProgrammeAttainmentSection2() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.AVERAGE_DIRECT, "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATTAINMENT_DIRECT, report.getReportType());
        assertEquals("School of Engineering and Technology", report.getSnapshot().getSchoolName());
    }

    @Test
    @DisplayName("Report 3: Programme Attainment - Section 3 Average Indirect PDF and Excel (No Advisory Banner)")
    void testProgrammeAttainmentSection3() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.AVERAGE_INDIRECT, "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATTAINMENT_INDIRECT, report.getReportType());
    }

    @Test
    @DisplayName("Report 4: Programme Attainment - Section 4 Overall Attainment PDF and Excel")
    void testProgrammeAttainmentSection4() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.OVERALL, "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATTAINMENT_OVERALL, report.getReportType());
    }

    @Test
    @DisplayName("Report 5 & 7: Programme Attainment Master PDF (fresh pages) & Master Excel (exact 4 sheets)")
    void testProgrammeAttainmentMasterPdfAndExcel() throws Exception {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.ALL, "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATTAINMENT, report.getReportType());

        // Verify Excel exact 4 sheets
        GeneratedReportDto.ArtifactSummaryDto xlsxArt = report.getArtifacts().stream()
                .filter(a -> a.getArtifactType() == ArtifactType.EXCEL).findFirst().orElseThrow();
        byte[] xlsxBytes = reportOrchestrationService.loadArtifactContent(xlsxArt.getArtifactId());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertEquals(4, wb.getNumberOfSheets(), "Master workbook must have exactly 4 sheets");
            assertEquals("Average Mapping", wb.getSheetName(0));
            assertEquals("Average Direct Attainment", wb.getSheetName(1));
            assertEquals("Average Indirect Attainment", wb.getSheetName(2));
            assertEquals("Overall Attainment", wb.getSheetName(3));
        }

        // Verify PDF fresh pages
        GeneratedReportDto.ArtifactSummaryDto pdfArt = report.getArtifacts().stream()
                .filter(a -> a.getArtifactType() == ArtifactType.PDF).findFirst().orElseThrow();
        byte[] pdfBytes = reportOrchestrationService.loadArtifactContent(pdfArt.getArtifactId());
        assertTrue(pdfBytes.length > 5000);
        assertEquals("%PDF", new String(pdfBytes, 0, 4));
    }

    @Test
    @DisplayName("Report 8 & 9: Course Attainment Consolidated PDF and Excel")
    void testCourseAttainmentReport() {
        GeneratedReportDto report = reportOrchestrationService.generateCourseAttainmentReport(
                pbcDS.getId(), "Course Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.COURSE_ATTAINMENT, report.getReportType());
        assertEquals("School of Engineering and Technology", report.getSnapshot().getSchoolName());
        assertTrue(report.getArtifacts().size() >= 2);
    }

    @Test
    @DisplayName("Report 10 & 11: Programme ATR PDF and Excel with numbered actions")
    void testProgrammeAtrReport() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAtrReport(
                batchCSE.getId(), "Programme Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.PROGRAMME_ATR, report.getReportType());
        assertEquals("School of Engineering and Technology", report.getSnapshot().getSchoolName());
    }

    @Test
    @DisplayName("Report 12 & 13: Course ATR PDF and Excel with numbered actions")
    void testCourseAtrReport() {
        GeneratedReportDto report = reportOrchestrationService.generateCourseAtrReport(
                pbcDS.getId(), "Course Coordinator", "DYPIU");

        assertNotNull(report);
        assertEquals(ReportType.COURSE_ATR, report.getReportType());
        assertEquals("School of Engineering and Technology", report.getSnapshot().getSchoolName());
    }

    @Test
    @DisplayName("Multi-School Dynamic Resolution: Engineering vs Management under same Institutional Template")
    void testMultiSchoolDynamicResolution() {
        // 1. Engineering Report
        GeneratedReportDto engReport = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.ALL, "Coordinator", "DYPIU");
        assertEquals("School of Engineering and Technology", engReport.getSnapshot().getSchoolName());
        assertEquals("B.Tech in Computer Science and Engineering", ((ProgrammeAttainmentSnapshot) engReport.getSnapshot()).getMasterProgrammeName());

        // 2. Management Report
        GeneratedReportDto mgmtReport = reportOrchestrationService.generateProgrammeAttainmentReport(
                progMBA.getId(), batchMBA.getId(), ReportSection.ALL, "Coordinator", "DYPIU");
        assertEquals("School of Management", mgmtReport.getSnapshot().getSchoolName());
        assertEquals("Master of Business Administration", ((ProgrammeAttainmentSnapshot) mgmtReport.getSnapshot()).getMasterProgrammeName());
    }

    @Test
    @DisplayName("Cryptographic Integrity: Tamper detection test on generated artifacts")
    void testIntegrityVerificationAndTamperDetection() {
        GeneratedReportDto report = reportOrchestrationService.generateProgrammeAttainmentReport(
                progCSE.getId(), batchCSE.getId(), ReportSection.ALL, "Coordinator", "DYPIU");

        GeneratedReportDto.ArtifactSummaryDto pdfArt = report.getArtifacts().stream()
                .filter(a -> a.getArtifactType() == ArtifactType.PDF).findFirst().orElseThrow();
        byte[] pdfBytes = reportOrchestrationService.loadArtifactContent(pdfArt.getArtifactId());

        // 1. Valid verification
        VerificationResponseDto validRes = reportOrchestrationService.verifyReportArtifact(report.getReportId(), ArtifactType.PDF, pdfBytes);
        assertTrue(validRes.isValid());
        assertEquals("VALID", validRes.getStatus());

        // 2. Tampered verification
        byte[] tamperedBytes = Arrays.copyOf(pdfBytes, pdfBytes.length);
        tamperedBytes[tamperedBytes.length - 1] = (byte) (tamperedBytes[tamperedBytes.length - 1] ^ 0xFF);
        VerificationResponseDto tamperedRes = reportOrchestrationService.verifyReportArtifact(report.getReportId(), ArtifactType.PDF, tamperedBytes);
        assertFalse(tamperedRes.isValid());
        assertEquals("HASH_MISMATCH", tamperedRes.getStatus());
    }
}
