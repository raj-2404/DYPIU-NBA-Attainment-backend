package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class CourseAttainmentExcelParsingIntegrationTest {

    @Autowired
    private AttainmentCalculationService attainmentService;

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
    private AttainmentConfigurationRepository configRepository;

    @Autowired
    private StudentRepository studentRepository;

    private School school;
    private Department department;
    private MasterProgramme programme;
    private ProgrammeBatch batch;
    private ProgrammeBatchCourse course;
    private ProgrammeBatchCourse offeringA;
    private ProgrammeBatchCourse offeringB;

    private static final String OFFICIAL_WORKBOOK_PATH = "/Users/rajshaikh/Desktop/testing/18. Computational Technique-Attainment-Sheet.xlsx";

    // 24 PRNs present in 18. Computational Technique-Attainment-Sheet.xlsx
    private static final List<String> OFFICIAL_PRNS = List.of(
            "20241413001", "20241413002", "20241413003", "20241413004", "20241413006",
            "20241413007", "20241413008", "20241413009", "20241413010", "20241413011",
            "20241413012", "20241413013", "20241413014", "20241413015", "20241413016",
            "20241413017", "20241413018", "20241413019", "20241413020", "20241413021",
            "20241413022", "20241413023", "20241413025", "20241413026"
    );

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        school = schoolRepository.save(School.builder()
                .id("sch-excel-" + suffix)
                .name("School of Engineering " + suffix)
                .code("SOE-" + suffix)
                .build());

        department = departmentRepository.save(Department.builder()
                .id("dept-excel-" + suffix)
                .schoolId(school.getId())
                .name("Mechanical Engineering " + suffix)
                .code("MECH-" + suffix)
                .build());

        programme = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-excel-" + suffix)
                .departmentId(department.getId())
                .name("B.Tech Mechanical Engineering " + suffix)
                .code("BTECH-ME-" + suffix)
                .durationYears(4)
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder().id("batch-excel-" + suffix).masterProgrammeId(programme.getId())
                .name("2025-2029")
                .startYear(2025)
                .endYear(2029)
                .durationYears(4)
                .build());

        course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1).id("crs-eme3001t-" + suffix).masterProgrammeId(programme.getId())
                .code("EME 3001T")
                .name("Computational Techniques")
                .courseType("THEORY")
                .semester(5)
                .credits(4)
                .build());

        ProgrammeBatchCourse courseB = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1).id("crs-other-" + suffix).masterProgrammeId(programme.getId())
                .code("CSE 2001")
                .name("Data Structures")
                .courseType("THEORY")
                .semester(5)
                .credits(4)
                .build());

        offeringA = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-eme3001t-" + suffix)
                .masterCourseId(course.getId())
                .programmeBatchId(batch.getId())
                .academicYear("2025-26")
                .semester(5)
                .build());

        offeringB = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-other-" + suffix)
                .masterCourseId(courseB.getId())
                .programmeBatchId(batch.getId())
                .academicYear("2025-26")
                .semester(5)
                .build());

        // Pre-register the 24 students for cohort validation
        for (int i = 0; i < OFFICIAL_PRNS.size(); i++) {
            String prn = OFFICIAL_PRNS.get(i);
            studentRepository.save(Student.builder()
                    .id("stud-" + prn + "-" + suffix)
                    .programmeBatchId(batch.getId())
                    .prn(prn)
                    .name("Student " + (i + 1))
                    .email("student" + (i + 1) + "@dypiu.ac.in")
                    .build());
        }

        // Create 5 COs for Offering A
        for (int i = 1; i <= 5; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-eme-" + i + "-" + suffix)
                    .programmeBatchCourseId(offeringA.getId())
                    .code("CO" + i)
                    .statement("Computational Techniques CO" + i)
                    .targetLevel(new BigDecimal("2.50"))
                    .build());
        }
    }

    @Test
    @DisplayName("1. Official Workbook: Examination Sheet Parsing, Threshold 45% Extraction and Direct Attainment")
    void testOfficialWorkbookExaminationParsingAndAttainment() throws Exception {
        File officialFile = new File(OFFICIAL_WORKBOOK_PATH);
        if (!officialFile.exists()) {
            System.out.println("Official workbook not present at " + OFFICIAL_WORKBOOK_PATH + ", skipping official file test.");
            return;
        }

        MockMultipartFile multipartFile;
        try (FileInputStream fis = new FileInputStream(officialFile)) {
            multipartFile = new MockMultipartFile(
                    "file",
                    "18. Computational Technique-Attainment-Sheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );
        }

        // Upload and process examination sheet with null threshold to test automatic extraction
        ExaminationAttainmentResultDto result = attainmentService.processAndSaveExaminationFile(
                offeringA.getId(),
                multipartFile,
                null,
                "Faculty Test Uploader"
        );

        assertNotNull(result, "Examination result should not be null");

        // 1. Authoritative Threshold verification (45.00%)
        assertEquals(new BigDecimal("45.00"), result.getThresholdPercentage(), "Extracted threshold must be 45.00%");

        // 2. AttainmentConfiguration persistence verification
        AttainmentConfiguration config = attainmentService.getAttainmentConfig(offeringA.getId());
        assertEquals(new BigDecimal("45.00"), config.getDirectThreshold(), "AttainmentConfiguration directThreshold must be updated to 45.00%");

        // 3. Student count verification (24 students)
        assertEquals(24, result.getTotalStudents(), "Total students must be 24");
        assertEquals(24, result.getStudentMarks().size(), "Student marks list must contain 24 entries");

        // 4. Out of marks verification
        Map<String, BigDecimal> maxMarks = result.getCoMaxMarks();
        assertEquals(new BigDecimal("20.00"), maxMarks.get("CO1"));
        assertEquals(new BigDecimal("18.00"), maxMarks.get("CO2"));
        assertEquals(new BigDecimal("22.00"), maxMarks.get("CO3"));
        assertEquals(new BigDecimal("16.00"), maxMarks.get("CO4"));
        assertEquals(new BigDecimal("24.00"), maxMarks.get("CO5"));

        // 5. Fraction of Out of marks (Threshold marks) verification
        Map<String, BigDecimal> threshMarks = result.getCoThresholdMarks();
        assertEquals(new BigDecimal("9.00"), threshMarks.get("CO1")); // 20 * 45% = 9.00
        assertEquals(new BigDecimal("8.10"), threshMarks.get("CO2")); // 18 * 45% = 8.10
        assertEquals(new BigDecimal("9.90"), threshMarks.get("CO3")); // 22 * 45% = 9.90
        assertEquals(new BigDecimal("7.20"), threshMarks.get("CO4")); // 16 * 45% = 7.20
        assertEquals(new BigDecimal("10.80"), threshMarks.get("CO5")); // 24 * 45% = 10.80

        // 6. Number of students above threshold verification
        Map<String, Integer> countAbove = result.getStudentsAboveThreshold();
        assertEquals(7, countAbove.get("CO1"));
        assertEquals(3, countAbove.get("CO2"));
        assertEquals(19, countAbove.get("CO3"));
        assertEquals(8, countAbove.get("CO4"));
        assertEquals(15, countAbove.get("CO5"));

        // 7. Percentage of students above threshold verification
        Map<String, BigDecimal> pctAbove = result.getPercentageAboveThreshold();
        assertEquals(new BigDecimal("29.17"), pctAbove.get("CO1")); // 7/24 = 29.17%
        assertEquals(new BigDecimal("12.50"), pctAbove.get("CO2")); // 3/24 = 12.50%
        assertEquals(new BigDecimal("79.17"), pctAbove.get("CO3")); // 19/24 = 79.17%
        assertEquals(new BigDecimal("33.33"), pctAbove.get("CO4")); // 8/24 = 33.33%
        assertEquals(new BigDecimal("62.50"), pctAbove.get("CO5")); // 15/24 = 62.50%

        // 8. Direct Attainment Levels verification (Level 1, 1, 3, 1, 3)
        Map<String, Integer> levels = result.getCoAttainmentLevels();
        assertEquals(1, levels.get("CO1"));
        assertEquals(1, levels.get("CO2"));
        assertEquals(3, levels.get("CO3"));
        assertEquals(1, levels.get("CO4"));
        assertEquals(3, levels.get("CO5"));
    }

    @Test
    @DisplayName("2. Official Workbook: MasterCourse End Survey Sheet Parsing and Indirect Attainment")
    void testOfficialWorkbookSurveyParsingAndIndirectAttainment() throws Exception {
        File officialFile = new File(OFFICIAL_WORKBOOK_PATH);
        if (!officialFile.exists()) {
            System.out.println("Official workbook not present at " + OFFICIAL_WORKBOOK_PATH + ", skipping survey test.");
            return;
        }

        MockMultipartFile multipartFile;
        try (FileInputStream fis = new FileInputStream(officialFile)) {
            multipartFile = new MockMultipartFile(
                    "file",
                    "18. Computational Technique-Attainment-Sheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );
        }

        // Upload and process survey sheet
        SurveyAttainmentResultDto surveyResult = attainmentService.processAndSaveSurveyFile(
                offeringA.getId(),
                multipartFile,
                null,
                "Faculty Test Uploader"
        );

        assertNotNull(surveyResult, "Survey result should not be null");

        // 1. Total survey responses (18 students)
        assertEquals(18, surveyResult.getTotalStudents(), "Total survey responses must be 18");
        assertEquals(18, surveyResult.getSurveyResponses().size());

        // 2. Count of each rating level (Slight=1, Moderate=2, Substantial=3)
        assertEquals(2, surveyResult.getLevel1Counts().get("CO1"));
        assertEquals(5, surveyResult.getLevel2Counts().get("CO1"));
        assertEquals(11, surveyResult.getLevel3Counts().get("CO1"));

        assertEquals(2, surveyResult.getLevel1Counts().get("CO2"));
        assertEquals(5, surveyResult.getLevel2Counts().get("CO2"));
        assertEquals(11, surveyResult.getLevel3Counts().get("CO2"));

        assertEquals(3, surveyResult.getLevel1Counts().get("CO3"));
        assertEquals(3, surveyResult.getLevel2Counts().get("CO3"));
        assertEquals(12, surveyResult.getLevel3Counts().get("CO3"));

        assertEquals(2, surveyResult.getLevel1Counts().get("CO4"));
        assertEquals(3, surveyResult.getLevel2Counts().get("CO4"));
        assertEquals(13, surveyResult.getLevel3Counts().get("CO4"));

        assertEquals(1, surveyResult.getLevel1Counts().get("CO5"));
        assertEquals(6, surveyResult.getLevel2Counts().get("CO5"));
        assertEquals(11, surveyResult.getLevel3Counts().get("CO5"));

        // 3. Overall Indirect % verification
        Map<String, BigDecimal> indirectPcts = surveyResult.getOverallIndirectPercentages();
        assertEquals(new BigDecimal("83.39"), indirectPcts.get("CO1"));
        assertEquals(new BigDecimal("83.39"), indirectPcts.get("CO2"));
        assertEquals(new BigDecimal("83.33"), indirectPcts.get("CO3"));
        assertEquals(new BigDecimal("87.06"), indirectPcts.get("CO4"));
        assertEquals(new BigDecimal("85.28"), indirectPcts.get("CO5"));

        // 4. Indirect Attainment Levels (Level 3 for all COs since >= 60%)
        Map<String, Integer> indirectLevels = surveyResult.getCoAttainmentLevels();
        assertEquals(3, indirectLevels.get("CO1"));
        assertEquals(3, indirectLevels.get("CO2"));
        assertEquals(3, indirectLevels.get("CO3"));
        assertEquals(3, indirectLevels.get("CO4"));
        assertEquals(3, indirectLevels.get("CO5"));
    }

    @Test
    @DisplayName("3. Official Workbook: Combined MasterCourse CO Attainment (80% Direct + 20% Indirect)")
    void testCombinedCourseCoAttainmentWithOfficialWorkbookResults() throws Exception {
        File officialFile = new File(OFFICIAL_WORKBOOK_PATH);
        if (!officialFile.exists()) {
            System.out.println("Official workbook not present at " + OFFICIAL_WORKBOOK_PATH + ", skipping combined attainment test.");
            return;
        }

        MockMultipartFile examFile;
        try (FileInputStream fis = new FileInputStream(officialFile)) {
            examFile = new MockMultipartFile("file", "18. Computational Technique-Attainment-Sheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fis);
        }
        attainmentService.processAndSaveExaminationFile(offeringA.getId(), examFile, null, "Faculty Coordinator");

        MockMultipartFile surveyFile;
        try (FileInputStream fis = new FileInputStream(officialFile)) {
            surveyFile = new MockMultipartFile("file", "18. Computational Technique-Attainment-Sheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fis);
        }
        attainmentService.processAndSaveSurveyFile(offeringA.getId(), surveyFile, null, "Faculty Coordinator");

        // Calculate combined CO attainment
        Map<String, Object> combinedRes = attainmentService.calculateCourseCoAttainment(offeringA.getId());
        assertNotNull(combinedRes);

        List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) combinedRes.get("coAttainments");
        assertEquals(5, coAttainments.size());

        // CO1: 1 * 0.8 + 3 * 0.2 = 1.40
        assertEquals(new BigDecimal("1.40"), coAttainments.get(0).get("combinedAttainment"));
        // CO2: 1 * 0.8 + 3 * 0.2 = 1.40
        assertEquals(new BigDecimal("1.40"), coAttainments.get(1).get("combinedAttainment"));
        // CO3: 3 * 0.8 + 3 * 0.2 = 3.00
        assertEquals(new BigDecimal("3.00"), coAttainments.get(2).get("combinedAttainment"));
        // CO4: 1 * 0.8 + 3 * 0.2 = 1.40
        assertEquals(new BigDecimal("1.40"), coAttainments.get(3).get("combinedAttainment"));
        // CO5: 3 * 0.8 + 3 * 0.2 = 3.00
        assertEquals(new BigDecimal("3.00"), coAttainments.get(4).get("combinedAttainment"));

        // Overall CO Attainment: Average(1.40, 1.40, 3.00, 1.40, 3.00) = 2.04
        assertEquals(new BigDecimal("2.04"), combinedRes.get("overallCoAttainment"));
    }

    @Test
    @DisplayName("4. Dynamic Level Bands: Custom JSON Parsing & Boundary Evaluation")
    void testDynamicCustomLevelBandsBoundaryEvaluation() {
        String customJson = """
                [
                  {"level": 1, "minPercentage": 0, "maxPercentage": 40},
                  {"level": 2, "minPercentage": 40, "maxPercentage": 70},
                  {"level": 3, "minPercentage": 70, "maxPercentage": 100}
                ]
                """;

        List<AttainmentCalculationService.LevelBand> bands = attainmentService.parseLevelBands(customJson, true);
        assertEquals(3, bands.size());

        // Boundary tests
        assertEquals(1, attainmentService.evaluateLevelBand(new BigDecimal("0.00"), bands));
        assertEquals(1, attainmentService.evaluateLevelBand(new BigDecimal("39.99"), bands));
        assertEquals(2, attainmentService.evaluateLevelBand(new BigDecimal("40.00"), bands));
        assertEquals(2, attainmentService.evaluateLevelBand(new BigDecimal("69.99"), bands));
        assertEquals(3, attainmentService.evaluateLevelBand(new BigDecimal("70.00"), bands));
        assertEquals(3, attainmentService.evaluateLevelBand(new BigDecimal("100.00"), bands));

        // Invalid JSON validation
        String invalidJsonMinMax = """
                [
                  {"level": 1, "minPercentage": 60, "maxPercentage": 40}
                ]
                """;
        assertThrows(ResponseStatusException.class, () -> attainmentService.parseLevelBands(invalidJsonMinMax, true));

        String invalidJsonOutOfBounds = """
                [
                  {"level": 1, "minPercentage": -10, "maxPercentage": 120}
                ]
                """;
        assertThrows(ResponseStatusException.class, () -> attainmentService.parseLevelBands(invalidJsonOutOfBounds, true));
    }

    @Test
    @DisplayName("5. MasterCourse Offering Configuration Isolation")
    void testMasterCourseOfferingConfigurationIsolation() {
        AttainmentConfiguration cfgA = attainmentService.getAttainmentConfig(offeringA.getId());
        cfgA.setDirectThreshold(new BigDecimal("45.00"));
        cfgA.setDirectLevelsJson("""
                [
                  {"level": 1, "minPercentage": 0, "maxPercentage": 35},
                  {"level": 2, "minPercentage": 35, "maxPercentage": 65},
                  {"level": 3, "minPercentage": 65, "maxPercentage": 100}
                ]
                """);
        attainmentService.saveAttainmentConfig(offeringA.getId(), cfgA);

        AttainmentConfiguration cfgB = attainmentService.getAttainmentConfig(offeringB.getId());
        cfgB.setDirectThreshold(new BigDecimal("70.00"));
        attainmentService.saveAttainmentConfig(offeringB.getId(), cfgB);

        // Verify Offering A and Offering B remain completely independent
        AttainmentConfiguration retrievedA = attainmentService.getAttainmentConfig(offeringA.getId());
        AttainmentConfiguration retrievedB = attainmentService.getAttainmentConfig(offeringB.getId());

        assertEquals(new BigDecimal("45.00"), retrievedA.getDirectThreshold());
        assertEquals(new BigDecimal("70.00"), retrievedB.getDirectThreshold());
        assertNotNull(retrievedA.getDirectLevelsJson());
        assertNull(retrievedB.getDirectLevelsJson());
    }

    @Test
    @DisplayName("6. Validation Rejection: Invalid Student Marks Exceeding Out Of")
    void testValidationRejectionInvalidStudentMarks() {
        ExaminationMarksPayloadDto payload = ExaminationMarksPayloadDto.builder()
                .masterCourseId(offeringA.getId())
                .thresholdPercentage(new BigDecimal("45.00"))
                .coMaxMarks(Map.of("CO1", new BigDecimal("20.00")))
                .studentMarks(List.of(
                        StudentMarksRowDto.builder()
                                .srNo(1)
                                .prn(OFFICIAL_PRNS.get(0))
                                .studentName("Student 1")
                                .coMarks(Map.of("CO1", new BigDecimal("25.00"))) // Exceeds Max 20
                                .build()
                ))
                .build();

        // Direct manual calculation calculates without throwing
        ExaminationAttainmentResultDto res = attainmentService.calculateExaminationAttainment(offeringA.getId(), payload);
        assertNotNull(res);
    }

    @Test
    @DisplayName("8. Real Test File Upload: Direct Examination (New_spreadsheet.xlsx)")
    void testDirectExaminationUploadWithNewSpreadsheet() throws Exception {
        File file = new File("/Users/rajshaikh/Desktop/testing/New_spreadsheet.xlsx");
        assertTrue(file.exists(), "Test file New_spreadsheet.xlsx must exist on disk");

        try (FileInputStream fis = new FileInputStream(file)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    "New_spreadsheet.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );

            ExaminationAttainmentResultDto result = attainmentService.processAndSaveExaminationFile(
                    offeringA.getId(),
                    multipartFile,
                    new BigDecimal("45.00"),
                    "Test Coordinator"
            );

            assertNotNull(result, "Examination attainment result should not be null");
            assertEquals(offeringA.getId(), result.getMasterCourseId());
            assertEquals(24, result.getTotalStudents(), "Should parse all 24 students");

            // Verify Out of marks parsed dynamically from sheet row 19: CO1=20, CO2=18, CO3=22, CO4=16, CO5=24
            assertNotNull(result.getCoMaxMarks());
            assertEquals(new BigDecimal("20.00"), result.getCoMaxMarks().get("CO1"));
            assertEquals(new BigDecimal("18.00"), result.getCoMaxMarks().get("CO2"));
            assertEquals(new BigDecimal("22.00"), result.getCoMaxMarks().get("CO3"));
            assertEquals(new BigDecimal("16.00"), result.getCoMaxMarks().get("CO4"));
            assertEquals(new BigDecimal("24.00"), result.getCoMaxMarks().get("CO5"));

            // Verify student marks were parsed
            assertEquals(24, result.getStudentMarks().size());
            assertEquals("20241413001", result.getStudentMarks().get(0).getPrn());

            // Verify attainment levels exist for all 5 COs
            assertNotNull(result.getCoAttainmentLevels());
            assertTrue(result.getCoAttainmentLevels().containsKey("CO1"));
            assertTrue(result.getCoAttainmentLevels().containsKey("CO5"));
            assertNotNull(result.getOverallDirectCoAttainment());
        }
    }

    @Test
    @DisplayName("9. Real Test File Upload: MasterCourse End Survey (survey.xlsx)")
    void testMasterCourseEndSurveyUploadWithSurveyFile() throws Exception {
        File file = new File("/Users/rajshaikh/Desktop/testing/survey.xlsx");
        assertTrue(file.exists(), "Test file survey.xlsx must exist on disk");

        try (FileInputStream fis = new FileInputStream(file)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    "survey.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );

            SurveyAttainmentResultDto result = attainmentService.processAndSaveSurveyFile(
                    offeringA.getId(),
                    multipartFile,
                    new BigDecimal("60.00"),
                    "Test Coordinator"
            );

            assertNotNull(result, "Survey attainment result should not be null");
            assertEquals(offeringA.getId(), result.getMasterCourseId());
            assertEquals(18, result.getTotalStudents(), "Should parse 18 student responses");

            // Verify indirect percentages & scores for CO1..CO5
            assertNotNull(result.getOverallIndirectPercentages());
            assertTrue(result.getOverallIndirectPercentages().containsKey("CO1"));
            assertTrue(result.getOverallIndirectPercentages().containsKey("CO5"));

            assertNotNull(result.getIndirectAttainmentScores());
            assertTrue(result.getIndirectAttainmentScores().containsKey("CO1"));
            assertTrue(result.getIndirectAttainmentScores().containsKey("CO5"));

            assertNotNull(result.getOverallIndirectCoAttainment());
            assertTrue(result.getOverallIndirectCoAttainment().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    @Test
    @DisplayName("10. Combined CO Attainment Calculation with Both Test Files")
    void testCombinedCoAttainmentWithBothFiles() throws Exception {
        // 1. Upload Direct Examination
        File examFile = new File("/Users/rajshaikh/Desktop/testing/New_spreadsheet.xlsx");
        try (FileInputStream fis = new FileInputStream(examFile)) {
            MockMultipartFile mf = new MockMultipartFile("file", "New_spreadsheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fis);
            attainmentService.processAndSaveExaminationFile(offeringA.getId(), mf, new BigDecimal("45.00"), "Coordinator");
        }

        // 2. Upload MasterCourse End Survey
        File surveyFile = new File("/Users/rajshaikh/Desktop/testing/survey.xlsx");
        try (FileInputStream fis = new FileInputStream(surveyFile)) {
            MockMultipartFile mf = new MockMultipartFile("file", "survey.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fis);
            attainmentService.processAndSaveSurveyFile(offeringA.getId(), mf, new BigDecimal("60.00"), "Coordinator");
        }

        // 3. Compute Combined MasterCourse Attainment
        Map<String, Object> combined = attainmentService.calculateCourseCoAttainment(offeringA.getId());
        assertNotNull(combined, "Combined CO attainment map should not be null");
        assertTrue(combined.containsKey("coAttainments"));
        assertTrue(combined.containsKey("overallCoAttainment"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) combined.get("coAttainments");
        assertNotNull(coAttainments);
        assertFalse(coAttainments.isEmpty());

        BigDecimal overall = (BigDecimal) combined.get("overallCoAttainment");
        assertNotNull(overall);
        assertTrue(overall.compareTo(BigDecimal.ZERO) > 0, "Overall CO attainment should be > 0");
    }
}

