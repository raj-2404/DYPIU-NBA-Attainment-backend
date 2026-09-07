package com.dypiu.nba.service;

import com.dypiu.nba.dto.CourseOfferingRequestDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CourseOfferingOverridesIntegrationTest {

    @Autowired
    private AcademicService academicService;

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

    private School testSchool;
    private Department testDept;
    private MasterProgramme testProg;
    private ProgrammeBatch testBatch0;
    private ProgrammeBatch testBatch1;
    private ProgrammeBatch testBatch2;
    private ProgrammeBatchCourse testMasterCourse;
    private User testFaculty;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        testSchool = schoolRepository.save(School.builder()
                .id("sch-ovr-" + uid)
                .name("School of Engineering")
                .code("SOE-" + uid)
                .build());

        testDept = departmentRepository.save(Department.builder()
                .id("dept-ovr-" + uid)
                .name("Computer Science")
                .code("CS-" + uid)
                .schoolId(testSchool.getId())
                .build());

        testProg = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-ovr-" + uid)
                .name("B.Tech Computer Science")
                .code("BT-CSE-" + uid)
                .departmentId(testDept.getId())
                .durationYears(4)
                .build());

        testBatch1 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-ovr1-" + uid)
                .masterProgrammeId(testProg.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        testBatch2 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-ovr2-" + uid)
                .masterProgrammeId(testProg.getId())
                .name("2025-2029")
                .startYear(2025)
                .endYear(2029)
                .build());

        ProgrammeBatch templateBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-template-" + uid)
                .masterProgrammeId(testProg.getId())
                .name("Template Batch")
                .startYear(2020)
                .endYear(2024)
                .build());

        testMasterCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("crs-ovr-" + uid)
                .programmeBatchId(templateBatch.getId())
                .semester(1)
                .masterProgrammeId(testProg.getId())
                .code("CS101")
                .name("Data Structures & Algorithms")
                .credits(4)
                .courseType("CORE")
                .build());

        testFaculty = userRepository.save(User.builder()
                .username("cc1_soe_" + uid)
                .email("cc1_soe_" + uid + "@gmail.com")
                .name("Prof. Coordinator " + uid)
                .passwordHash("password")
                .role(UserRole.FACULTY)
                .departmentId(testDept.getId())
                .build());
    }

    @Test
    @DisplayName("1. Create course offering with code and name overrides, verifying effective display and raw overrides")
    void testCreateCourseOfferingWithOverrides() {
        CourseOfferingRequestDto request = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .courseCodeOverride("CS301")
                .courseNameOverride("Advanced Data Structures")
                .semester(3)
                .academicYear("2025-26")
                .courseCoordinatorId(testFaculty.getId())
                .assignedFaculty(List.of(testFaculty.getEmail()))
                .build();

        ProgrammeBatchCourse created = academicService.createCourseOffering(request);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("CS301", created.getCourseCodeOverride());
        assertEquals("Advanced Data Structures", created.getCourseNameOverride());
        assertEquals("CS301", created.getCourseCode(), "Effective courseCode must equal courseCodeOverride");
        assertEquals("Advanced Data Structures", created.getCourseName(), "Effective courseName must equal courseNameOverride");
        assertEquals("ACTIVE", created.getStatus(), "Default status must be assigned");

        // Verify MasterCourse remains strictly unchanged
        ProgrammeBatchCourse master = programmeBatchCourseRepository.findById(testMasterCourse.getId()).orElseThrow();
        assertEquals("CS101", master.getCode());
        assertEquals("Data Structures & Algorithms", master.getName());
    }

    @Test
    @DisplayName("2. Create course offering without overrides, verifying fallback to MasterCourse code and name")
    void testCreateCourseOfferingWithoutOverridesFallback() {
        CourseOfferingRequestDto request = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch2.getId())
                .masterCourseId(testMasterCourse.getId())
                .courseCodeOverride(null)
                .courseNameOverride("   ") // blank string should convert to null
                .semester(1)
                .build();

        ProgrammeBatchCourse created = academicService.createCourseOffering(request);

        assertNotNull(created);
        assertNull(created.getCourseCodeOverride());
        assertNull(created.getCourseNameOverride());
        assertEquals("CS101", created.getCourseCode(), "Effective courseCode must fallback to MasterCourse code");
        assertEquals("Data Structures & Algorithms", created.getCourseName(), "Effective courseName must fallback to MasterCourse name");

        // Verify MasterCourse remains unchanged
        ProgrammeBatchCourse master = programmeBatchCourseRepository.findById(testMasterCourse.getId()).orElseThrow();
        assertEquals("CS101", master.getCode());
        assertEquals("Data Structures & Algorithms", master.getName());
    }

    @Test
    @DisplayName("3. Update course offering overrides and verify effective values")
    void testUpdateCourseOfferingOverrides() {
        // Create initial offering without overrides
        CourseOfferingRequestDto createReq = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .semester(2)
                .build();

        ProgrammeBatchCourse offering = academicService.createCourseOffering(createReq);
        assertEquals("CS101", offering.getCourseCode());
        assertEquals("Data Structures & Algorithms", offering.getCourseName());

        // Update with overrides
        CourseOfferingRequestDto updateReq = CourseOfferingRequestDto.builder()
                .courseCodeOverride("  CS201-ADV  ")
                .courseNameOverride("  DSA Honours  ")
                .build();

        ProgrammeBatchCourse updated = academicService.updateCourseOffering(offering.getId(), updateReq);

        assertEquals("CS201-ADV", updated.getCourseCodeOverride(), "Whitespace must be trimmed");
        assertEquals("DSA Honours", updated.getCourseNameOverride(), "Whitespace must be trimmed");
        assertEquals("CS201-ADV", updated.getCourseCode());
        assertEquals("DSA Honours", updated.getCourseName());

        // Clear overrides back to null
        CourseOfferingRequestDto clearReq = CourseOfferingRequestDto.builder()
                .courseCodeOverride("")
                .courseNameOverride("")
                .build();

        ProgrammeBatchCourse cleared = academicService.updateCourseOffering(offering.getId(), clearReq);
        assertNull(cleared.getCourseCodeOverride());
        assertNull(cleared.getCourseNameOverride());
        assertEquals("CS101", cleared.getCourseCode(), "Effective courseCode must fallback after clearing");
        assertEquals("Data Structures & Algorithms", cleared.getCourseName(), "Effective courseName must fallback after clearing");

        // Verify MasterCourse is still unchanged
        ProgrammeBatchCourse master = programmeBatchCourseRepository.findById(testMasterCourse.getId()).orElseThrow();
        assertEquals("CS101", master.getCode());
        assertEquals("Data Structures & Algorithms", master.getName());
    }

    @Test
    @DisplayName("4. Same MasterCourse used in multiple batches with different overrides")
    void testSameMasterCourseMultipleBatchesIndependentOverrides() {
        CourseOfferingRequestDto reqBatch1 = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .courseCodeOverride("CS-B1-101")
                .courseNameOverride("DSA for 2024 Batch")
                .semester(1)
                .build();

        CourseOfferingRequestDto reqBatch2 = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch2.getId())
                .masterCourseId(testMasterCourse.getId())
                .courseCodeOverride("CS-B2-101")
                .courseNameOverride("DSA for 2025 Batch")
                .semester(1)
                .build();

        ProgrammeBatchCourse off1 = academicService.createCourseOffering(reqBatch1);
        ProgrammeBatchCourse off2 = academicService.createCourseOffering(reqBatch2);

        assertEquals("CS-B1-101", off1.getCourseCode());
        assertEquals("DSA for 2024 Batch", off1.getCourseName());

        assertEquals("CS-B2-101", off2.getCourseCode());
        assertEquals("DSA for 2025 Batch", off2.getCourseName());

        // Master Course unchanged
        ProgrammeBatchCourse master = programmeBatchCourseRepository.findById(testMasterCourse.getId()).orElseThrow();
        assertEquals("CS101", master.getCode());
        assertEquals("Data Structures & Algorithms", master.getName());
    }

    @Test
    @DisplayName("5. Duplicate offering for same (programmeBatchId, masterCourseId) is rejected")
    void testDuplicateOfferingRejected() {
        CourseOfferingRequestDto req1 = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .semester(1)
                .build();

        academicService.createCourseOffering(req1);

        CourseOfferingRequestDto req2 = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .semester(2)
                .build();

        assertThrows(ResponseStatusException.class, () -> academicService.createCourseOffering(req2));
    }

    @Test
    @DisplayName("6. Missing programmeBatchId or masterCourseId is rejected")
    void testMissingRequiredFieldsRejected() {
        CourseOfferingRequestDto noBatch = CourseOfferingRequestDto.builder()
                .masterCourseId(testMasterCourse.getId())
                .semester(1)
                .build();
        assertThrows(ResponseStatusException.class, () -> academicService.createCourseOffering(noBatch));

        CourseOfferingRequestDto noCourse = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .semester(1)
                .build();
        assertThrows(ResponseStatusException.class, () -> academicService.createCourseOffering(noCourse));
    }

    @Test
    @DisplayName("7. Fetching offerings returns effective courseCode and courseName and overrides")
    void testGetOfferingsReturnsEffectiveAndOverrides() {
        CourseOfferingRequestDto req = CourseOfferingRequestDto.builder()
                .programmeBatchId(testBatch1.getId())
                .masterCourseId(testMasterCourse.getId())
                .courseCodeOverride("CS301")
                .courseNameOverride("Advanced DSA")
                .semester(4)
                .build();

        ProgrammeBatchCourse created = academicService.createCourseOffering(req);

        // Test GET by offering ID
        ProgrammeBatchCourse fetched = academicService.getProgrammeBatchCourseById(created.getId());
        assertEquals("CS301", fetched.getCourseCode());
        assertEquals("Advanced DSA", fetched.getCourseName());
        assertEquals("CS301", fetched.getCourseCodeOverride());
        assertEquals("Advanced DSA", fetched.getCourseNameOverride());

        // Test GET list by batch ID
        List<ProgrammeBatchCourse> list = academicService.getProgrammeBatchCoursesByBatch(testBatch1.getId());
        assertFalse(list.isEmpty());
        ProgrammeBatchCourse item = list.stream().filter(o -> o.getId().equals(created.getId())).findFirst().orElseThrow();
        assertEquals("CS301", item.getCourseCode());
        assertEquals("Advanced DSA", item.getCourseName());
        assertEquals("CS301", item.getCourseCodeOverride());
        assertEquals("Advanced DSA", item.getCourseNameOverride());
    }
}
