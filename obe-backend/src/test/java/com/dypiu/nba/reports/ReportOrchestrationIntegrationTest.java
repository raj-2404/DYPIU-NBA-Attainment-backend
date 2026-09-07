package com.dypiu.nba.reports;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.reports.model.*;
import com.dypiu.nba.reports.repository.ReportArtifactRepository;
import com.dypiu.nba.reports.repository.ReportRepository;
import com.dypiu.nba.reports.service.ReportOrchestrationService;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "iqac_user", roles = {"IQAC"})
public class ReportOrchestrationIntegrationTest {

    @Autowired
    private ReportOrchestrationService orchestrationService;

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
    private CourseOutcomeRepository courseOutcomeRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportArtifactRepository artifactRepository;

    private String batchId;
    private String progId;
    private String offeringId;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        School school = schoolRepository.save(School.builder()
                .id("sch-" + uid)
                .code("SOET_" + uid)
                .name("School of Engineering")
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-" + uid)
                .schoolId(school.getId())
                .code("CSE_" + uid)
                .name("Computer Science & Engineering")
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-" + uid)
                .departmentId(dept.getId())
                .code("BTECH-CSE")
                .name("B.Tech in Computer Science")
                .status("ACTIVE")
                .build());
        progId = prog.getId();

        userRepository.save(User.builder()
                .username("iqac_user")
                .email("iqac@dypiu.ac.in")
                .name("IQAC Coordinator")
                .passwordHash("hashed")
                .role(UserRole.IQAC)
                .schoolId(school.getId())
                .departmentId(dept.getId())
                .masterProgrammeId(progId)
                .isActive(true)
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-" + uid)
                .masterProgrammeId(progId)
                .programmeName("B.Tech CSE")
                .name("B.Tech CSE 2021-25")
                .startYear(2021)
                .endYear(2025)
                .build());
        batchId = batch.getId();

        ProgrammeBatchCourse course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("course-" + uid)
                .masterProgrammeId(progId)
                .code("CS201")
                .name("Data Structures")
                .credits(4)
                .build());

        ProgrammeBatchCourse pbc = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("pbc-" + uid)
                .programmeBatchId(batchId)
                .masterCourseId(course.getId())
                .semester(3)
                .build());
        offeringId = pbc.getId();

        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-" + uid)
                .programmeBatchCourseId(offeringId)
                .code("CO1")
                .statement("Analyze time and space complexity")
                .targetLevel(new BigDecimal("2.50"))
                .status(ApprovalStatus.APPROVED)
                .build());
    }

    @Test
    @DisplayName("Orchestration Service generates Programme Attainment Master Report with PDF and Excel artifacts")
    void testGenerateProgrammeAttainmentMaster() {
        GeneratedReportDto report = orchestrationService.generateProgrammeAttainmentReport(
                progId, batchId, ReportSection.ALL, "admin_user", "DYPIU");

        assertNotNull(report);
        assertNotNull(report.getReportId());
        assertEquals(ReportType.PROGRAMME_ATTAINMENT, report.getReportType());
        assertEquals(2, report.getArtifacts().size());

        ReportEntity entity = reportRepository.findById(report.getReportId()).orElse(null);
        assertNotNull(entity);
        assertEquals("GENERATED", entity.getStatus());
        assertNotNull(entity.getSnapshotJson());

        List<ReportArtifactEntity> artifacts = artifactRepository.findByReportId(report.getReportId());
        assertEquals(2, artifacts.size());

        for (ReportArtifactEntity a : artifacts) {
            assertNotNull(a.getSha256Checksum());
            assertEquals(64, a.getSha256Checksum().length());
            assertNotNull(a.getHmacSignature());
            assertEquals(64, a.getHmacSignature().length());
            byte[] fileBytes = orchestrationService.loadArtifactContent(a.getId());
            assertNotNull(fileBytes);
            assertTrue(fileBytes.length > 0);
        }
    }

    @Test
    @DisplayName("Cryptographic Verification Endpoint validates authentic artifact and catches tampered file")
    void testCryptographicVerification() {
        GeneratedReportDto report = orchestrationService.generateCourseAttainmentReport(
                offeringId, "admin_user", "DYPIU");

        GeneratedReportDto.ArtifactSummaryDto pdfSummary = report.getArtifacts().stream()
                .filter(a -> a.getArtifactType() == ArtifactType.PDF)
                .findFirst().orElseThrow();

        byte[] originalPdf = orchestrationService.loadArtifactContent(pdfSummary.getArtifactId());

        // 1. Verify authentic file
        VerificationResponseDto validResult = orchestrationService.verifyReportArtifact(
                report.getReportId(), ArtifactType.PDF, originalPdf);
        assertTrue(validResult.isValid());
        assertEquals("VALID", validResult.getStatus());

        // 2. Tamper with bytes
        byte[] tamperedPdf = originalPdf.clone();
        tamperedPdf[tamperedPdf.length - 1] = (byte) (tamperedPdf[tamperedPdf.length - 1] ^ 0xFF);

        VerificationResponseDto tamperedResult = orchestrationService.verifyReportArtifact(
                report.getReportId(), ArtifactType.PDF, tamperedPdf);
        assertFalse(tamperedResult.isValid());
        assertEquals("HASH_MISMATCH", tamperedResult.getStatus());
    }
}
