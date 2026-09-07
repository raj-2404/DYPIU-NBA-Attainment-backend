package com.dypiu.nba;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
public class AcademicSoftDeleteIntegrationTest {

    @Autowired
    private AcademicService academicService;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    private String deptId;

    @BeforeEach
    void setUp() {
        if (schoolRepository.count() == 0) {
            School school = new School();
            school.setId("sch-1");
            school.setCode("S1");
            school.setName("School 1");
            schoolRepository.save(school);
        }
        if (departmentRepository.count() == 0) {
            Department dept = new Department();
            dept.setId("dept-1");
            dept.setSchoolId("sch-1");
            dept.setCode("D1");
            dept.setName("Dept 1");
            dept.setStatus("ACTIVE");
            departmentRepository.save(dept);
            deptId = dept.getId();
        } else {
            deptId = departmentRepository.findAll().get(0).getId();
        }
    }

    @Test
    void testSoftDeleteAndRecreateProgramme() {
        // 1. Create a MasterProgramme
        MasterProgramme p1 = new MasterProgramme();
        p1.setDepartmentId(deptId);
        p1.setCode("B.TECH-TEST");
        p1.setName("Test Prog 1");
        MasterProgramme saved1 = academicService.saveProgramme(p1);
        String oldId = saved1.getId();

        // 2. Delete it (should soft-delete)
        academicService.deleteProgramme(oldId);

        // Verify it is soft-deleted
        Optional<MasterProgramme> deletedP1 = masterProgrammeRepository.findById(oldId);
        assertThat(deletedP1).isPresent();
        assertThat(deletedP1.get().getDeletedAt()).isNotNull();

        // Verify it is excluded from active listings
        List<MasterProgramme> activeProgs = masterProgrammeRepository.findByDeletedAtIsNull();
        assertThat(activeProgs).extracting(MasterProgramme::getId).doesNotContain(oldId);

        // 3. Create a NEW MasterProgramme with the exact same code
        MasterProgramme p2 = new MasterProgramme();
        p2.setDepartmentId(deptId);
        p2.setCode("B.TECH-TEST");
        p2.setName("Test Prog 2");
        
        MasterProgramme saved2 = academicService.saveProgramme(p2);
        String newId = saved2.getId();

        // Verify NEW ID is created
        assertThat(newId).isNotNull().isNotEqualTo(oldId);

        // Verify both exist in the database with the SAME code
        Optional<MasterProgramme> fromDbNew = masterProgrammeRepository.findById(newId);
        assertThat(fromDbNew).isPresent();
        assertThat(fromDbNew.get().getCode()).isEqualTo("B.TECH-TEST");
        assertThat(fromDbNew.get().getDeletedAt()).isNull();

        Optional<MasterProgramme> fromDbOld = masterProgrammeRepository.findById(oldId);
        assertThat(fromDbOld).isPresent();
        assertThat(fromDbOld.get().getCode()).isEqualTo("B.TECH-TEST");
        assertThat(fromDbOld.get().getDeletedAt()).isNotNull();
    }

    @Autowired
    private ProgrammeBatchRepository programmeBatchRepository;

    @Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;
@Test
    void testSoftDeleteAndRecreateProgrammeBatch() {
        // 1. Create a MasterProgramme
        MasterProgramme prog = MasterProgramme.builder()
                .departmentId(deptId)
                .code("CSE-BATCH-TEST")
                .name("Computer Science Batch Test")
                .build();
        MasterProgramme savedProg = academicService.saveProgramme(prog);

        // 2. Create a Batch
        ProgrammeBatch batch1 = ProgrammeBatch.builder()
                .id("batch-test-2022")
                .masterProgrammeId(savedProg.getId())
                .name("Batch 2022-2026")
                .startYear(2022)
                .endYear(2026)
                .durationYears(4)
                .build();
        ProgrammeBatch savedBatch1 = academicService.saveBatch(batch1);
        assertThat(savedBatch1.getId()).isEqualTo("batch-test-2022");

        // 3. Delete the Batch (soft delete)
        academicService.deleteBatch(savedBatch1.getId());
        Optional<ProgrammeBatch> softDeletedOpt = programmeBatchRepository.findById("batch-test-2022");
        assertThat(softDeletedOpt).isPresent();
        assertThat(softDeletedOpt.get().getDeletedAt()).isNotNull();

        // 4. Recreate the Batch with the SAME ID and same start_year
        ProgrammeBatch batchRecreate = ProgrammeBatch.builder()
                .id("batch-test-2022")
                .masterProgrammeId(savedProg.getId())
                .name("Batch 2022-2026 Recreated")
                .startYear(2022)
                .endYear(2026)
                .durationYears(4)
                .build();
        ProgrammeBatch revivedBatch = academicService.saveBatch(batchRecreate);

        assertThat(revivedBatch).isNotNull();
        assertThat(revivedBatch.getId()).isEqualTo("batch-test-2022");
        assertThat(revivedBatch.getDeletedAt()).isNull();
        assertThat(revivedBatch.getName()).isEqualTo("Batch 2022-2026 Recreated");

        // 5. Test recreating with auto-generated ID for the same start_year after soft deleting
        academicService.deleteBatch("batch-test-2022");
        ProgrammeBatch batchAutoId = ProgrammeBatch.builder()
                .masterProgrammeId(savedProg.getId())
                .name("Batch 2022-2026 New")
                .startYear(2022)
                .endYear(2026)
                .durationYears(4)
                .build();
        ProgrammeBatch revivedAuto = academicService.saveBatch(batchAutoId);
        assertThat(revivedAuto).isNotNull();
        assertThat(revivedAuto.getDeletedAt()).isNull();
        assertThat(revivedAuto.getStartYear()).isEqualTo(2022);
    }

    @Test
    void testSoftDeleteAndRecreateCourseOffering() {
        // 1. Create Prog & Batch & MasterCourse
        MasterProgramme prog = MasterProgramme.builder()
                .departmentId(deptId)
                .code("CSE-OFF-TEST")
                .name("Computer Science Offering Test")
                .build();
        MasterProgramme savedProg = academicService.saveProgramme(prog);

        ProgrammeBatch batch = ProgrammeBatch.builder()
                .masterProgrammeId(savedProg.getId())
                .name("Batch 2023-2027")
                .startYear(2023)
                .endYear(2027)
                .durationYears(4)
                .build();
        ProgrammeBatch savedBatch = academicService.saveBatch(batch);

        
        

        // 2. Create Course Offering
        com.dypiu.nba.dto.CourseOfferingRequestDto req = com.dypiu.nba.dto.CourseOfferingRequestDto.builder()
                .programmeBatchId(savedBatch.getId())
                .code("CS201").name("Data Structures").credits(4).courseType("CORE")
                .semester(3)
                .build();
        ProgrammeBatchCourse offering = academicService.createCourseOffering(req);
        assertThat(offering).isNotNull();

        // 3. Delete Course Offering (soft delete)
        academicService.deleteProgrammeBatchCourse(offering.getId());
        Optional<ProgrammeBatchCourse> deletedOpt = programmeBatchCourseRepository.findById(offering.getId());
        assertThat(deletedOpt).isPresent();
        assertThat(deletedOpt.get().getDeletedAt()).isNotNull();

        // 4. Recreate Course Offering for same batch and master course
        com.dypiu.nba.dto.CourseOfferingRequestDto req2 = com.dypiu.nba.dto.CourseOfferingRequestDto.builder()
                .programmeBatchId(savedBatch.getId())
                .code("CS201").name("Data Structures").credits(4).courseType("CORE")
                .semester(3)
                .courseCoordinatorName("Dr. Instructor")
                .build();
        ProgrammeBatchCourse revivedOffering = academicService.createCourseOffering(req2);
        assertThat(revivedOffering).isNotNull();
        assertThat(revivedOffering.getDeletedAt()).isNull();
        assertThat(revivedOffering.getCourseCoordinatorName()).isEqualTo("Dr. Instructor");
    }
}
