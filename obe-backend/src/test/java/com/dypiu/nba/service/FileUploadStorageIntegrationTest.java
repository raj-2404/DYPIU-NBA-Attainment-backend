package com.dypiu.nba.service;

import com.dypiu.nba.dto.ExaminationAttainmentResultDto;
import com.dypiu.nba.dto.SurveyAttainmentResultDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FileUploadStorageIntegrationTest {

    @Autowired
    private AttainmentCalculationService attainmentCalculationService;

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:${app.upload.dir:./target/test-uploads}}")
    private String baseUploadDir;

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

    private ProgrammeBatchCourse testOffering;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        School school = schoolRepository.save(School.builder()
                .id("sch-up-" + uid)
                .name("School of Engineering")
                .code("SOE-" + uid)
                .build());

        Department dept = departmentRepository.save(Department.builder()
                .id("dept-up-" + uid)
                .name("Computer Science")
                .code("CS-" + uid)
                .schoolId(school.getId())
                .build());

        MasterProgramme prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-up-" + uid)
                .name("B.Tech Computer Science")
                .code("BT-CSE-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        ProgrammeBatch batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-up-" + uid)
                .masterProgrammeId(prog.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        ProgrammeBatchCourse course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("crs-up-" + uid)
                .masterProgrammeId(prog.getId())
                .code("CS201")
                .name("Data Structures")
                .credits(4)
                .build());

        testOffering = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("offering-8dd53799")
                .programmeBatchId(batch.getId())
                .masterCourseId(course.getId())
                .semester(3)
                .status("ACTIVE")
                .build());

        for (int i = 1; i <= 5; i++) {
            courseOutcomeRepository.save(CourseOutcome.builder()
                    .id("co-up-" + uid + "-" + i)
                    .programmeBatchCourseId(testOffering.getId())
                    .code("CO" + i)
                    .statement("Course Outcome " + i)
                    .build());
        }

        for (int i = 1; i <= 50; i++) {
            String prn = String.format("20241413%03d", i);
            studentRepository.save(Student.builder()
                    .id("std-up1-" + uid + "-" + i)
                    .prn(prn)
                    .name("Student " + i)
                    .programmeBatchId(batch.getId())
                    .email("student" + i + "@dypiu.ac.in")
                    .status(StudentStatus.ENROLLED)
                    .build());
        }

        for (int i = 0; i <= 35; i++) {
            String prn = String.valueOf(202412345677L + i);
            studentRepository.save(Student.builder()
                    .id("std-up2-" + uid + "-" + i)
                    .prn(prn)
                    .name("Student " + (i + 1))
                    .programmeBatchId(batch.getId())
                    .email("student_b" + i + "@dypiu.ac.in")
                    .status(StudentStatus.ENROLLED)
                    .build());
        }
    }

    @Test
    @DisplayName("Verify examination upload stores file in /Users/rajshaikh/Desktop/uploads/examination/offering-8dd53799/")
    void testExaminationUploadStorageLocation() throws Exception {
        File excelSource = new File("/Users/rajshaikh/Desktop/testing/student_marks.xlsx");
        byte[] content;
        if (excelSource.exists()) {
            try (FileInputStream fis = new FileInputStream(excelSource)) {
                content = fis.readAllBytes();
            }
        } else {
            content = "dummy test excel content".getBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "student_marks.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        ExaminationAttainmentResultDto result = attainmentCalculationService.processAndSaveExaminationFile(
                "offering-8dd53799",
                file,
                null,
                "Test Coordinator"
        );

        assertNotNull(result);

        Path expectedDir = Paths.get(baseUploadDir, "examination", "offering-8dd53799");
        assertTrue(Files.exists(expectedDir), "Directory must exist: " + expectedDir);
        assertTrue(Files.isDirectory(expectedDir), "Path must be a directory: " + expectedDir);

        // Verify stored file exists inside target directory
        try (var stream = Files.list(expectedDir)) {
            var files = stream.filter(p -> p.getFileName().toString().endsWith("_student_marks.xlsx")).toList();
            assertFalse(files.isEmpty(), "At least one saved student_marks.xlsx file must exist in " + expectedDir);
            Path storedFile = files.get(0);
            assertTrue(Files.size(storedFile) > 0, "Stored file must not be empty");
        }
    }

    @Test
    @DisplayName("Verify survey upload stores file in target directory")
    void testSurveyUploadStorageLocation() throws Exception {
        File surveySource = new File("/Users/rajshaikh/Desktop/testing/survey.xlsx");
        byte[] content;
        if (surveySource.exists()) {
            try (FileInputStream fis = new FileInputStream(surveySource)) {
                content = fis.readAllBytes();
            }
        } else {
            content = "dummy test survey content".getBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "survey.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        SurveyAttainmentResultDto result = attainmentCalculationService.processAndSaveSurveyFile(
                "offering-8dd53799",
                file,
                null,
                "Test Coordinator"
        );

        assertNotNull(result);

        Path expectedDir = Paths.get(baseUploadDir, "survey", "offering-8dd53799");
        assertTrue(Files.exists(expectedDir), "Directory must exist: " + expectedDir);
        assertTrue(Files.isDirectory(expectedDir), "Path must be a directory: " + expectedDir);

        try (var stream = Files.list(expectedDir)) {
            var files = stream.filter(p -> p.getFileName().toString().endsWith("_survey.xlsx")).toList();
            assertFalse(files.isEmpty(), "At least one saved survey.xlsx file must exist in " + expectedDir);
        }
    }
}
