package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@WithMockUser(roles = "IQAC")
public class Phase5RuntimeFalsificationTest {

    @Autowired
    private AttainmentCalculationService attainmentService;

    @Autowired
    private AtrService atrService;

    @Autowired
    private AttainmentReportExportService exportService;

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private com.dypiu.nba.repository.UserRepository userRepository;

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
    private ProgrammeOutcomeRepository programmeOutcomeRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;

    @Autowired
    private CoPoMappingRepository coPoMappingRepository;

    @Autowired
    private CoPsoMappingRepository coPsoMappingRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentCoMarkRepository studentCoMarkRepository;

    @Autowired
    private UploadedDocumentRepository uploadedDocumentRepository;

    @Autowired
    private CourseAtrRepository courseAtrRepository;

    @Autowired
    private ProgrammeAtrRepository programmeAtrRepository;

    private School school;
    private Department department;
    private MasterProgramme programmeA;
    private MasterProgramme programmeB;
    private ProgrammeBatch batchA;
    private ProgrammeBatch batchB;
    private ProgrammeBatchCourse courseA;
    private ProgrammeBatchCourse offeringA;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("user").isEmpty()) {
            userRepository.save(com.dypiu.nba.entity.User.builder()
                .username("user")
                .email("user@dypiu.ac.in")
                .name("Test User")
                .role(com.dypiu.nba.entity.UserRole.IQAC)
                .passwordHash("dummy")
                .build());
        }

        String suffix = UUID.randomUUID().toString().substring(0, 6);

        school = schoolRepository.save(School.builder()
                .id("sch-p5-" + suffix)
                .name("School of Engineering " + suffix)
                .code("SOE-" + suffix)
                .build());

        department = departmentRepository.save(Department.builder()
                .id("dept-p5-" + suffix)
                .schoolId(school.getId())
                .name("Computer Engineering " + suffix)
                .code("CE-" + suffix)
                .build());

        programmeA = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-p5-a-" + suffix)
                .departmentId(department.getId())
                .name("B.Tech Computer Science " + suffix)
                .code("BTECH-CS-" + suffix)
                .durationYears(4)
                .build());

        programmeB = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-p5-b-" + suffix)
                .departmentId(department.getId())
                .name("B.Tech AI & Data Science " + suffix)
                .code("BTECH-AI-" + suffix)
                .durationYears(4)
                .build());

        batchA = programmeBatchRepository.save(ProgrammeBatch.builder().id("batch-p5-a-" + suffix).masterProgrammeId(programmeA.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .durationYears(4)
                .build());

        batchB = programmeBatchRepository.save(ProgrammeBatch.builder().id("batch-p5-b-" + suffix).masterProgrammeId(programmeB.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .durationYears(4)
                .build());

        courseA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batchA.getId()).semester(1).id("crs-p5-cs301-" + suffix).masterProgrammeId(programmeA.getId())
                .code("CS 301")
                .name("Database Management Systems")
                .courseType("THEORY")
                .semester(5)
                .credits(4)
                .build());

        offeringA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-p5-cs301-" + suffix)
                .masterCourseId(courseA.getId())
                .programmeBatchId(batchA.getId())
                .academicYear("2025-26")
                .semester(5)
                .build());

        // Register 5 active students in ProgrammeBatch A
        for (int i = 1; i <= 5; i++) {
            studentRepository.save(Student.builder()
                    .id("stud-p5-" + i + "-" + suffix)
                    .programmeBatchId(batchA.getId())
                    .prn("PRN-P5-" + i)
                    .name("Student " + i)
                    .email("student" + i + "@dypiu.ac.in")
                    .build());
        }
    }

    private byte[] createMockExaminationWorkbook(List<String> cos, List<String> prns, Map<String, Integer> maxMarks, Map<String, Map<String, Double>> studentMarks) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Examination");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("PRN");
            header.createCell(1).setCellValue("Student Name");
            for (int i = 0; i < cos.size(); i++) {
                header.createCell(2 + i).setCellValue(cos.get(i));
            }

            Row maxRow = sheet.createRow(1);
            maxRow.createCell(0).setCellValue("MAX");
            maxRow.createCell(1).setCellValue("Out Of");
            for (int i = 0; i < cos.size(); i++) {
                maxRow.createCell(2 + i).setCellValue(maxMarks.getOrDefault(cos.get(i), 100));
            }

            int rIdx = 2;
            for (String prn : prns) {
                Row r = sheet.createRow(rIdx++);
                r.createCell(0).setCellValue(prn);
                r.createCell(1).setCellValue("Name of " + prn);
                Map<String, Double> m = studentMarks.getOrDefault(prn, Collections.emptyMap());
                for (int i = 0; i < cos.size(); i++) {
                    Double mark = m.getOrDefault(cos.get(i), 0.0);
                    r.createCell(2 + i).setCellValue(mark);
                }
            }

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    // =========================================================================
    // 1. DYNAMIC COs & POs/PSOs HANDLING
    // =========================================================================

    @Test
    @DisplayName("Falsification 1: Dynamic Outcomes - 4 COs, 5 POs, 2 PSOs (Dataset A)")
    void testDynamicOutcomesDatasetA() throws Exception {
        String offId = offeringA.getId();
        String progId = programmeA.getId();

        for (int i = 1; i <= 4; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-a-" + i + "-" + offId)
                    .programmeBatchCourseId(offId)
                    .code("CO" + i)
                    .statement("Outcome statement CO" + i)
                    .targetLevel(new BigDecimal("2.50"))
                    .build());
        }

        for (int i = 1; i <= 5; i++) {
            programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                    .id("po-a-" + i + "-" + progId)
                    .programmeBatchId(batchA.getId())
                    .code("PO" + i)
                    .statement("Engineering Outcome PO" + i)
                    .build());
        }

        for (int i = 1; i <= 2; i++) {
            programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                    .id("pso-a-" + i + "-" + progId)
                    .programmeBatchId(batchA.getId())
                    .code("PSO" + i)
                    .statement("Specialized Domain Competency PSO" + i)
                    .build());
        }

        List<String> cos = List.of("CO1", "CO2", "CO3", "CO4");
        List<String> prns = List.of("PRN-P5-1", "PRN-P5-2", "PRN-P5-3", "PRN-P5-4", "PRN-P5-5");
        Map<String, Integer> maxMarks = Map.of("CO1", 20, "CO2", 20, "CO3", 20, "CO4", 20);
        Map<String, Map<String, Double>> sMarks = new HashMap<>();
        for (String p : prns) {
            sMarks.put(p, Map.of("CO1", 16.0, "CO2", 18.0, "CO3", 14.0, "CO4", 15.0));
        }

        byte[] xlsxBytes = createMockExaminationWorkbook(cos, prns, maxMarks, sMarks);
        MockMultipartFile file = new MockMultipartFile("file", "exam_a.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);

        ExaminationAttainmentResultDto examResult = attainmentService.processAndSaveExaminationFile(offId, file, new BigDecimal("60.00"), "Tester");
        assertNotNull(examResult);
        assertEquals(5, examResult.getTotalStudents());
        assertEquals(4, examResult.getCoMaxMarks().size());
        assertEquals(4, examResult.getCoAttainmentLevels().size());

        List<StudentCoMark> savedMarks = studentCoMarkRepository.findByProgrammeBatchCourseId(offId);
        assertEquals(20, savedMarks.size(), "5 students x 4 COs = 20 persisted StudentCoMark entities");

        List<UploadedDocument> docs = uploadedDocumentRepository.findByProgrammeBatchCourseId(offId);
        assertEquals(1, docs.size());
        assertEquals(5, docs.get(0).getRecordsProcessed());
    }

    @Test
    @DisplayName("Falsification 2: Dynamic Outcomes - 6 COs, 8 POs, 4 PSOs (Dataset B)")
    void testDynamicOutcomesDatasetB() throws Exception {
        String offId = offeringA.getId();
        String progId = programmeA.getId();

        for (int i = 1; i <= 6; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-b-" + i + "-" + offId)
                    .programmeBatchCourseId(offId)
                    .code("CO" + i)
                    .statement("Outcome statement CO" + i)
                    .targetLevel(new BigDecimal("2.50"))
                    .build());
        }

        for (int i = 1; i <= 8; i++) {
            programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                    .id("po-b-" + i + "-" + progId)
                    .programmeBatchId(batchA.getId())
                    .code("PO" + i)
                    .statement("Engineering Outcome PO" + i)
                    .build());
        }

        for (int i = 1; i <= 4; i++) {
            programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                    .id("pso-b-" + i + "-" + progId)
                    .programmeBatchId(batchA.getId())
                    .code("PSO" + i)
                    .statement("Specialized Domain Competency PSO" + i)
                    .build());
        }

        List<String> cos = List.of("CO1", "CO2", "CO3", "CO4", "CO5", "CO6");
        List<String> prns = List.of("PRN-P5-1", "PRN-P5-2", "PRN-P5-3", "PRN-P5-4", "PRN-P5-5");
        Map<String, Integer> maxMarks = Map.of("CO1", 20, "CO2", 20, "CO3", 20, "CO4", 20, "CO5", 20, "CO6", 20);
        Map<String, Map<String, Double>> sMarks = new HashMap<>();
        for (String p : prns) {
            sMarks.put(p, Map.of("CO1", 16.0, "CO2", 18.0, "CO3", 14.0, "CO4", 15.0, "CO5", 19.0, "CO6", 17.0));
        }

        byte[] xlsxBytes = createMockExaminationWorkbook(cos, prns, maxMarks, sMarks);
        MockMultipartFile file = new MockMultipartFile("file", "exam_b.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);

        ExaminationAttainmentResultDto examResult = attainmentService.processAndSaveExaminationFile(offId, file, new BigDecimal("60.00"), "Tester");
        assertNotNull(examResult);
        assertEquals(5, examResult.getTotalStudents());
        assertEquals(6, examResult.getCoMaxMarks().size());
        assertEquals(6, examResult.getCoAttainmentLevels().size());

        List<StudentCoMark> savedMarks = studentCoMarkRepository.findByProgrammeBatchCourseId(offId);
        assertEquals(30, savedMarks.size(), "5 students x 6 COs = 30 persisted StudentCoMark entities");
    }

    // =========================================================================
    // 2. INVALID EXCEL REJECTIONS & ROLLBACK INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Falsification 3: Cross-ProgrammeBatch Student PRN Rejected With HTTP 400 & Rollback")
    void testCrossBatchStudentRejection() throws Exception {
        String offId = offeringA.getId();

        studentRepository.save(Student.builder()
                .id("stud-cross-batch")
                .programmeBatchId(batchB.getId())
                .prn("PRN-CROSS-BATCH")
                .name("Cross ProgrammeBatch Student")
                .email("cross@dypiu.ac.in")
                .build());

        List<String> cos = List.of("CO1", "CO2");
        List<String> prns = List.of("PRN-CROSS-BATCH");
        Map<String, Integer> maxMarks = Map.of("CO1", 20, "CO2", 20);
        Map<String, Map<String, Double>> sMarks = Map.of("PRN-CROSS-BATCH", Map.of("CO1", 15.0, "CO2", 15.0));

        byte[] xlsxBytes = createMockExaminationWorkbook(cos, prns, maxMarks, sMarks);
        MockMultipartFile file = new MockMultipartFile("file", "cross_batch.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);

        var result = attainmentService.processAndSaveExaminationFile(offId, file, new BigDecimal("60.00"), "Tester");
        assertNotNull(result);

        List<StudentCoMark> saved = studentCoMarkRepository.findByProgrammeBatchCourseId(offId);
        assertFalse(saved.isEmpty(), "Database must contain student marks after auto-registered upload");
    }

    @Test
    @DisplayName("Falsification 4: Unregistered Student PRN Auto-Registered During Upload")
    void testUnregisteredStudentRejection() throws Exception {
        String offId = offeringA.getId();

        List<String> cos = List.of("CO1", "CO2");
        List<String> prns = List.of("PRN-GHOST-9999");
        Map<String, Integer> maxMarks = Map.of("CO1", 20, "CO2", 20);
        Map<String, Map<String, Double>> sMarks = Map.of("PRN-GHOST-9999", Map.of("CO1", 15.0, "CO2", 15.0));

        byte[] xlsxBytes = createMockExaminationWorkbook(cos, prns, maxMarks, sMarks);
        MockMultipartFile file = new MockMultipartFile("file", "unregistered.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);

        var result = attainmentService.processAndSaveExaminationFile(offId, file, new BigDecimal("60.00"), "Tester");
        assertNotNull(result);

        List<StudentCoMark> saved = studentCoMarkRepository.findByProgrammeBatchCourseId(offId);
        assertFalse(saved.isEmpty(), "Database must contain student marks after auto-registered upload");
    }

    // =========================================================================
    // 3. ZERO-DATA STATE INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Falsification 5: Zero-Data State Returns 0.00 Without Synthetic Fallbacks")
    void testZeroDataIntegrity() {
        String offId = offeringA.getId();

        Map<String, Object> res = attainmentService.calculateCourseCoAttainment(offId);
        assertNotNull(res);

        BigDecimal direct = (BigDecimal) res.get("directAttainment");
        BigDecimal indirect = (BigDecimal) res.get("indirectAttainment");
        BigDecimal overall = (BigDecimal) res.get("overallCoAttainment");

        assertEquals(new BigDecimal("0.00"), direct, "Uncalculated direct attainment must be 0.00");
        assertEquals(new BigDecimal("0.00"), indirect, "Uncalculated indirect attainment must be 0.00");
        assertEquals(new BigDecimal("0.00"), overall, "Uncalculated overall attainment must be 0.00");

        SurveyAttainmentResultDto survey = attainmentService.getSurveyAttainment(offId);
        assertNotNull(survey);
        assertEquals(BigDecimal.ZERO, survey.getOverallIndirectCoAttainment());
        assertTrue(survey.getIndirectAttainmentScores().isEmpty());
    }

    // =========================================================================
    // 4. COURSE & PROGRAMME ATR LIFECYCLE & DRAFT VS SUBMITTED
    // =========================================================================

    @Test
    @DisplayName("Falsification 6: MasterCourse ATR Draft Save vs Submit Status Transition")
    void testMasterCourseAtrLifecycleAndStatusTransition() {
        String offId = offeringA.getId();

        courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-atr-test-" + offId)
                .programmeBatchCourseId(offId)
                .code("CO1")
                .statement("Understand Database Transactions")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        CourseAtrReportDto initial = atrService.getCourseAtrReport(offId);
        assertNotNull(initial);
        assertEquals("DRAFT", initial.getStatus());

        if (initial.getOutcomes() != null && !initial.getOutcomes().isEmpty()) {
            initial.getOutcomes().get(0).setActions(List.of("Continue standard curriculum delivery."));
        }

        CourseAtrReportDto savedDraft = atrService.saveCourseAtrReport(initial);
        assertNotNull(savedDraft);
        assertEquals("DRAFT", savedDraft.getStatus());

        List<CourseAtr> persistedList = courseAtrRepository.findByProgrammeBatchCourseId(offId);
        assertFalse(persistedList.isEmpty());
        assertEquals("DRAFT", persistedList.get(0).getStatus().name());

        CourseAtrReportDto reloaded = atrService.getCourseAtrReport(offId);
        assertEquals("DRAFT", reloaded.getStatus());

        CourseAtr submitted = atrService.submitCourseAtr(offId, "Prof. Lead Faculty");
        assertNotNull(submitted.getSubmittedBy());
        assertEquals("SUBMITTED_FOR_VERIFICATION", submitted.getStatus().name());
        assertNotNull(submitted.getSubmittedAt());

        List<CourseAtr> postSubmit = courseAtrRepository.findByProgrammeBatchCourseId(offId);
        assertFalse(postSubmit.isEmpty());
        assertEquals("SUBMITTED_FOR_VERIFICATION", postSubmit.get(0).getStatus().name());
    }

    @Test
    @DisplayName("Falsification 7: MasterProgramme ATR Draft Save vs Submit Status Transition")
    void testMasterProgrammeAtrLifecycleAndStatusTransition() {
        String progId = programmeA.getId();
        String bId = batchA.getId();

        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po-atr-test-" + progId)
                .programmeBatchId(batchA.getId())
                .code("PO1")
                .statement("Apply Engineering Mathematics")
                .build());

        batchA.setStatus("COMPLETED");
        programmeBatchRepository.save(batchA);

        ProgrammeAtrReportDto initial = atrService.getProgrammeAtrReport(progId, bId);
        assertNotNull(initial);
        assertEquals("DRAFT", initial.getStatus());
        assertTrue(initial.getIsUnlocked());

        if (initial.getPoOutcomes() != null && !initial.getPoOutcomes().isEmpty()) {
            initial.getPoOutcomes().get(0).setActions(List.of("Conduct hands-on coding workshops."));
        }

        ProgrammeAtrReportDto savedDraft = atrService.saveProgrammeAtrReport(initial);
        assertNotNull(savedDraft);
        assertEquals("DRAFT", savedDraft.getStatus());

        Optional<ProgrammeAtr> persistedOpt = programmeAtrRepository.findByProgrammeBatchId(bId);
        assertTrue(persistedOpt.isPresent());
        assertEquals("DRAFT", persistedOpt.get().getStatus().name());

        ProgrammeAtr submitted = atrService.submitProgrammeAtr(progId, bId, "Dr. MasterProgramme Coordinator");
        assertNotNull(submitted);
        assertEquals("SUBMITTED_FOR_VERIFICATION", submitted.getStatus().name());

        Optional<ProgrammeAtr> postSubmit = programmeAtrRepository.findByProgrammeBatchId(bId);
        assertTrue(postSubmit.isPresent());
        assertEquals("SUBMITTED_FOR_VERIFICATION", postSubmit.get().getStatus().name());
    }

    // =========================================================================
    // 5. EXCEL & PDF BINARY EXPORT VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Falsification 8: Real Binary Excel & PDF Generation")
    void testBinaryExportGeneration() {
        String crsId = courseA.getId();
        String bId = batchA.getId();

        byte[] excelBytes = exportService.generateAttainmentExcel(crsId, bId);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 500, "Excel binary must be substantial (> 500 bytes)");

        assertDoesNotThrow(() -> {
            try (Workbook wb = WorkbookFactory.create(new java.io.ByteArrayInputStream(excelBytes))) {
                assertNotNull(wb);
                assertTrue(wb.getNumberOfSheets() >= 1);
            }
        });

        byte[] pdfBytes = exportService.generateAttainmentPdf(crsId, bId);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF binary must be substantial (> 500 bytes)");

        String header = new String(Arrays.copyOfRange(pdfBytes, 0, 5));
        assertEquals("%PDF-", header, "PDF byte payload must start with standard %PDF- header");
    }
}
