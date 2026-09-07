package com.dypiu.nba.service;

import com.dypiu.nba.dto.ExaminationAttainmentResultDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ExcelMarksParserIntegrationTest {

    @Autowired
    private AttainmentCalculationService attainmentCalculationService;

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
    private StudentRepository studentRepository;

    @Autowired
    private ProgrammeOutcomeRepository programmeOutcomeRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;

    @Autowired
    private AttainmentConfigurationRepository attainmentConfigurationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Parse student_marks.xlsx strictly using backend AttainmentCalculationService")
    void testParseStudentMarksExcelFile() throws Exception {
        File excelFile = new File("/Users/rajshaikh/Desktop/testing/student_marks.xlsx");
        assertTrue(excelFile.exists(), "Target excel file must exist at /Users/rajshaikh/Desktop/testing/student_marks.xlsx");

        // 1. Seed prerequisite academic hierarchy
        String uid = UUID.randomUUID().toString().substring(0, 6);
        School school = schoolRepository.save(School.builder()
                .id("sch-parse-" + uid)
                .name("School of Parsing")
                .code("SOP-" + uid)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-parse-" + uid)
                .name("Department of Parsing")
                .code("DOP-" + uid)
                .schoolId(school.getId())
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-parse-" + uid)
                .name("B.Tech Data Science")
                .code("BT-DS-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-parse-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        // Pre-register students with PRNs matching the Excel sheet
        for (int i = 1; i <= 30; i++) {
            String prn = String.format("20241413%03d", i);
            studentRepository.save(Student.builder()
                    .id("stud-" + uid + "-" + i)
                    .programmeBatchId(batch.getId())
                    .prn(prn)
                    .name("Student " + i)
                    .email("student" + i + "@dypiu.ac.in")
                    .status(StudentStatus.ENROLLED)
                    .build());
        }
        // Also register any extra PRNs in the sheet
        studentRepository.save(Student.builder()
                .id("stud-" + uid + "-extra1")
                .programmeBatchId(batch.getId())
                .prn("202412345677")
                .name("Extra Student")
                .email("extra@dypiu.ac.in")
                .status(StudentStatus.ENROLLED)
                .build());

        ProgrammeBatchCourse course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("course-parse-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Machine Learning")
                .code("CS401-" + uid)
                .credits(4)
                .build());

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-parse-" + uid)
                .programmeBatchId(batch.getId())
                .masterCourseId(course.getId())
                .semester(5)
                .status("ACTIVE")
                .build());

        // Create Course Outcomes CO1-CO5 (matching test excel sheets)
        for (int i = 1; i <= 5; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-parse-" + uid + "-co" + i)
                    .programmeBatchCourseId(offering.getId())
                    .code("CO" + i)
                    .statement("Course outcome statement for CO" + i)
                    .targetLevel(new BigDecimal("2.50"))
                    .build());
        }

        attainmentConfigurationRepository.save(AttainmentConfiguration.builder()
                .id("config-parse-" + uid)
                .programmeBatchCourseId(offering.getId())
                .directWeight(new BigDecimal("80.00"))
                .indirectWeight(new BigDecimal("20.00"))
                .directThreshold(new BigDecimal("60.00"))
                .indirectThreshold(new BigDecimal("60.00"))
                .status(AttainmentConfigStatus.DRAFT)
                .build());

        // 2. Read actual excel file into MockMultipartFile
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(excelFile)) {
            bytes = fis.readAllBytes();
        }
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                excelFile.getName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        // 3. Execute backend parser
        System.out.println("========== STRICT BACKEND EXCEL PARSER EXECUTION START ==========");
        ExaminationAttainmentResultDto result = attainmentCalculationService.processAndSaveExaminationFile(
                offering.getId(),
                multipartFile,
                null,
                "Backend Automated Test Runner"
        );

        String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(jsonOutput);
        System.out.println("========== STRICT BACKEND EXCEL PARSER EXECUTION END ==========");

        // 4. Assertions
        assertNotNull(result, "Backend parser result should not be null");
        assertNotNull(result.getStudentMarks(), "Student marks list should not be null");
        assertFalse(result.getStudentMarks().isEmpty(), "Student marks list should contain records");
        assertNotNull(result.getTotalStudents(), "Total students count should be present");
        assertNotNull(result.getCoMaxMarks(), "CO Max marks should be calculated");
        assertNotNull(result.getOverallDirectCoAttainment(), "Overall direct attainment should be calculated");
    }

    @Test
    @DisplayName("Parse survey.xlsx strictly using backend AttainmentCalculationService")
    void testParseSurveyExcelFile() throws Exception {
        File surveyFile = new File("/Users/rajshaikh/Desktop/testing/survey.xlsx");
        assertTrue(surveyFile.exists(), "Target survey file must exist at /Users/rajshaikh/Desktop/testing/survey.xlsx");

        // 1. Seed prerequisite academic hierarchy
        String uid = UUID.randomUUID().toString().substring(0, 6);
        School school = schoolRepository.save(School.builder()
                .id("sch-srv-" + uid)
                .name("School of Survey")
                .code("SOS-" + uid)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-srv-" + uid)
                .name("Department of Survey")
                .code("DOS-" + uid)
                .schoolId(school.getId())
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-srv-" + uid)
                .name("B.Tech Computer Science")
                .code("BT-CS-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-srv-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        ProgrammeBatchCourse course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("course-srv-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Database Management Systems")
                .code("CS301-" + uid)
                .credits(4)
                .build());

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-srv-" + uid)
                .programmeBatchId(batch.getId())
                .masterCourseId(course.getId())
                .semester(5)
                .status("ACTIVE")
                .build());

        for (int i = 1; i <= 5; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-srv-" + uid + "-co" + i)
                    .programmeBatchCourseId(offering.getId())
                    .code("CO" + i)
                    .statement("Course outcome statement for CO" + i)
                    .targetLevel(new BigDecimal("2.50"))
                    .build());
        }

        attainmentConfigurationRepository.save(AttainmentConfiguration.builder()
                .id("config-srv-" + uid)
                .programmeBatchCourseId(offering.getId())
                .directWeight(new BigDecimal("80.00"))
                .indirectWeight(new BigDecimal("20.00"))
                .directThreshold(new BigDecimal("60.00"))
                .indirectThreshold(new BigDecimal("60.00"))
                .status(AttainmentConfigStatus.DRAFT)
                .build());

        // 2. Read actual survey excel file into MockMultipartFile
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(surveyFile)) {
            bytes = fis.readAllBytes();
        }
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                surveyFile.getName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        // 3. Execute backend survey parser
        System.out.println("========== STRICT BACKEND SURVEY EXCEL PARSER EXECUTION START ==========");
        com.dypiu.nba.dto.SurveyAttainmentResultDto result = attainmentCalculationService.processAndSaveSurveyFile(
                offering.getId(),
                multipartFile,
                null,
                "Backend Automated Test Runner"
        );

        String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(jsonOutput);
        System.out.println("========== STRICT BACKEND SURVEY EXCEL PARSER EXECUTION END ==========");

        // 4. Assertions
        assertNotNull(result, "Backend survey parser result should not be null");
        assertNotNull(result.getSurveyResponses(), "Survey responses list should not be null");
        assertFalse(result.getSurveyResponses().isEmpty(), "Survey responses list should contain records");
        assertNotNull(result.getTotalStudents(), "Total students count should be present");
        assertNotNull(result.getOverallIndirectCoAttainment(), "Overall indirect attainment score should be calculated");
    }

    @Test
    @DisplayName("Parse ProgrammeEnd-Survey.xlsx strictly using backend AttainmentCalculationService")
    void testParseProgrammeEndSurveyExcelFile() throws Exception {
        File programmeSurveyFile = new File("/Users/rajshaikh/Desktop/ProgrammeEnd-Survey.xlsx");
        assertTrue(programmeSurveyFile.exists(), "Target programme survey file must exist at /Users/rajshaikh/Desktop/ProgrammeEnd-Survey.xlsx");

        // 1. Seed prerequisite academic hierarchy
        String uid = UUID.randomUUID().toString().substring(0, 6);
        School school = schoolRepository.save(School.builder()
                .id("sch-pes-" + uid)
                .name("School of Engineering")
                .code("SOE-" + uid)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-pes-" + uid)
                .name("Department of Computer Science")
                .code("CSE-" + uid)
                .schoolId(school.getId())
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-pes-" + uid)
                .name("B.Tech Computer Science")
                .code("BT-CSE-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-pes-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        // Configure PO1-PO12 and PSO1-PSO3 for this batch
        for (int i = 1; i <= 12; i++) {
            programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                    .id("po-pes-" + uid + "-po" + i)
                    .programmeBatchId(batch.getId())
                    .code("PO" + i)
                    .statement("Program Outcome statement " + i)
                    .target(new BigDecimal("2.50"))
                    .build());
        }

        for (int i = 1; i <= 3; i++) {
            programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                    .id("pso-pes-" + uid + "-pso" + i)
                    .programmeBatchId(batch.getId())
                    .code("PSO" + i)
                    .statement("Program Specific Outcome statement " + i)
                    .target(new BigDecimal("2.50"))
                    .build());
        }

        // 2. Read actual excel file into MockMultipartFile
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(programmeSurveyFile)) {
            bytes = fis.readAllBytes();
        }

        try (FileInputStream fis = new FileInputStream(programmeSurveyFile);
             org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(fis)) {
            System.out.println("=== EXCEL SHEETS IN ProgrammeEnd-Survey.xlsx ===");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(i);
                System.out.println("Sheet " + i + ": " + s.getSheetName() + " (total rows in sheet: " + s.getLastRowNum() + ")");
                int nonEmptyCount = 0;
                for (int r = 0; r <= s.getLastRowNum(); r++) {
                    org.apache.poi.ss.usermodel.Row row = s.getRow(r);
                    if (row == null) continue;
                    boolean hasContent = false;
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                        if (cell != null && !cell.toString().trim().isEmpty()) {
                            hasContent = true;
                            break;
                        }
                    }
                    if (hasContent) {
                        nonEmptyCount++;
                        if (nonEmptyCount <= 25) {
                            StringBuilder sb = new StringBuilder("Row " + r + ": ");
                            for (int c = 0; c < row.getLastCellNum(); c++) {
                                org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                                String str = (cell != null) ? cell.toString().trim() : "";
                                if (!str.isEmpty()) {
                                    sb.append("[c").append(c).append(": ").append(str).append("] ");
                                }
                            }
                            System.out.println(sb);
                        }
                    }
                }
                System.out.println("Total non-empty rows in Sheet " + i + ": " + nonEmptyCount);
            }
            System.out.println("=== END EXCEL DUMP ===");
        }

        // Populate sample response rows into the template to verify full parsing calculation
        byte[] populatedBytes;
        try (FileInputStream fis = new FileInputStream(programmeSurveyFile);
             org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(fis)) {
            org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.Row r9 = s.createRow(9);
            r9.createCell(0).setCellValue(1);
            r9.createCell(1).setCellValue("20241413001");
            r9.createCell(2).setCellValue("Student One");
            for (int col = 3; col <= 17; col++) {
                r9.createCell(col).setCellValue(3.0); // Substantial (3)
            }

            org.apache.poi.ss.usermodel.Row r10 = s.createRow(10);
            r10.createCell(0).setCellValue(2);
            r10.createCell(1).setCellValue("20241413002");
            r10.createCell(2).setCellValue("Student Two");
            for (int col = 3; col <= 17; col++) {
                r10.createCell(col).setCellValue(2.0); // Moderate (2)
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            wb.write(baos);
            populatedBytes = baos.toByteArray();
        }

        MockMultipartFile populatedMultipartFile = new MockMultipartFile(
                "file",
                programmeSurveyFile.getName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                populatedBytes
        );

        System.out.println("========== STRICT BACKEND PROGRAMME SURVEY EXCEL PARSER (WITH ROWS) ==========");
        com.dypiu.nba.dto.ProgrammeSurveyResultDto result = attainmentCalculationService.processAndSaveProgrammeSurveyFile(
                prog.getId(),
                batch.getId(),
                populatedMultipartFile,
                "Backend Automated Test Runner"
        );

        String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(jsonOutput);
        System.out.println("========== STRICT BACKEND PROGRAMME SURVEY EXCEL PARSER EXECUTION END ==========");

        // 4. Assertions
        assertNotNull(result, "Backend programme survey parser result should not be null");
        assertTrue(result.getRecordsProcessed() > 0, "Records processed should be greater than 0");
        assertNotNull(result.getPoIndirectAttainment(), "PO Indirect Attainment list should not be null");
        assertNotNull(result.getPsoIndirectAttainment(), "PSO Indirect Attainment list should not be null");
    }
}
