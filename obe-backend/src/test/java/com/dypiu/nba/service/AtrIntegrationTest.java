package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.exception.ResourceNotFoundException;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AtrIntegrationTest {

    @Autowired
    private AtrService atrService;

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
    private CourseAtrRepository courseAtrRepository;

    @Autowired
    private ProgrammeAtrRepository programmeAtrRepository;

    @Autowired
    private ProgrammeOutcomeRepository programmeOutcomeRepository;

    @Autowired
    private ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;

    @Autowired
    private CoPoMappingRepository coPoMappingRepository;

    private String schoolId;
    private String deptId;
    private String masterProgrammeId;
    private String programmeBatchId;
    private String masterCourseId;
    private String offeringId;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);
        schoolId = "sch-" + uid;
        deptId = "dept-" + uid;
        masterProgrammeId = "prog-" + uid;
        programmeBatchId = "batch-" + uid;
        masterCourseId = "offering-" + uid;
        offeringId = "off-" + uid;

        schoolRepository.save(School.builder().id(schoolId).name("School of Tech " + uid).code("ST" + uid).build());
        departmentRepository.save(Department.builder().id(deptId).schoolId(schoolId).name("Dept " + uid).code("D" + uid).build());
        masterProgrammeRepository.save(MasterProgramme.builder().id(masterProgrammeId).departmentId(deptId).name("B.Tech " + uid).code("BT" + uid).build());
        programmeBatchRepository.save(ProgrammeBatch.builder().id(programmeBatchId).masterProgrammeId(masterProgrammeId).name("2022-2026").startYear(2022).endYear(2026).status("ACTIVE").build());

        
        programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().id(offeringId).masterCourseId(masterCourseId).programmeBatchId(programmeBatchId).semester(4).build());

        // Create 2 COs
        CourseOutcome co1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co1-" + uid).programmeBatchCourseId(offeringId).code("CO1").statement("Understand SQL").targetLevel(new BigDecimal("2.50")).build());
        CourseOutcome co2 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co2-" + uid).programmeBatchCourseId(offeringId).code("CO2").statement("Design Schema").targetLevel(new BigDecimal("2.50")).build());

        // Create POs and PSOs
        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po1-" + uid).programmeBatchId(masterProgrammeId).code("PO1").statement("Engineering Knowledge").build());
        programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                .id("pso1-" + uid).programmeBatchId(masterProgrammeId).code("PSO1").statement("Software Design").build());

        // Create Mappings
        coPoMappingRepository.save(CoPoMapping.builder().id("m1-" + uid).courseOutcomeId(co1.getId()).poCode("PO1").mappingLevel(3).build());
        coPoMappingRepository.save(CoPoMapping.builder().id("m2-" + uid).courseOutcomeId(co2.getId()).poCode("PO1").mappingLevel(2).build());
    }

    // =========================================================================
    //  COURSE ATR TESTS
    // =========================================================================

    @Test
    void testGetCourseAtrReport_DefaultAndCalculated() {
        CourseAtrReportDto report = atrService.getCourseAtrReport(offeringId);
        assertNotNull(report);
        assertEquals("COURSE_ATR", report.getReportType());
        assertEquals("DRAFT", report.getStatus());
        assertEquals(offeringId, report.getCourseOffering().getId());
        assertNotNull(report.getOutcomes());
        assertEquals(2, report.getOutcomes().size());

        CourseAtrReportDto.OutcomeRow row1 = report.getOutcomes().get(0);
        assertEquals("CO1", row1.getOutcomeCode());
        assertNotNull(row1.getTargetLevel());
        assertNotNull(row1.getAttainmentLevel());
        assertNotNull(row1.getActions());
        assertTrue(row1.getActions().isEmpty());
    }

    @Test
    void testSaveCourseAtrReport_PersistenceAndRetrieval() {
        CourseAtrReportDto report = atrService.getCourseAtrReport(offeringId);
        assertNotNull(report);

        // Edit actions for CO1
        report.getOutcomes().get(0).setActions(List.of("Custom Action 1: Add 2 laboratory sessions on indexing."));
        CourseAtrReportDto saved = atrService.saveCourseAtrReport(report);

        assertNotNull(saved);
        assertEquals(offeringId, saved.getCourseOffering().getId());

        // Verify DB persistence
        List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(offeringId);
        assertEquals(2, atrs.size());

        CourseAtr co1Atr = atrs.stream().filter(a -> "CO1".equals(a.getCoCode())).findFirst().orElse(null);
        assertNotNull(co1Atr);
        assertTrue(co1Atr.getActionsJson().contains("Custom Action 1: Add 2 laboratory sessions"));
    }

    @Test
    void testSaveCourseAtrReport_IdempotentUpdates() {
        CourseAtrReportDto report = atrService.getCourseAtrReport(offeringId);
        report.getOutcomes().get(0).setActions(List.of("First Action"));
        atrService.saveCourseAtrReport(report);

        assertEquals(2, courseAtrRepository.findByProgrammeBatchCourseId(offeringId).size());

        // Save again with modified action
        report.getOutcomes().get(0).setActions(List.of("Updated Action"));
        atrService.saveCourseAtrReport(report);

        List<CourseAtr> atrsAfter = courseAtrRepository.findByProgrammeBatchCourseId(offeringId);
        assertEquals(2, atrsAfter.size()); // no duplicates created

        CourseAtr co1Atr = atrsAfter.stream().filter(a -> "CO1".equals(a.getCoCode())).findFirst().orElse(null);
        assertNotNull(co1Atr);
        assertTrue(co1Atr.getActionsJson().contains("Updated Action"));
    }

    @Test
    void testSubmitCourseAtr_StatusTransition() {
        CourseAtr submitted = atrService.submitCourseAtr(offeringId, "Dr. Faculty");
        assertNotNull(submitted);
        assertEquals(CourseAtrStatus.SUBMITTED_FOR_VERIFICATION, submitted.getStatus());
        assertEquals("Dr. Faculty", submitted.getSubmittedBy());
        assertNotNull(submitted.getSubmittedAt());

        // Verify that report retrieval now reflects submitted status
        CourseAtrReportDto report = atrService.getCourseAtrReport(offeringId);
        assertEquals("SUBMITTED_FOR_VERIFICATION", report.getStatus());
    }

    @Test
    void testMasterCourseAtr_NotFound() {
        assertThrows(ResourceNotFoundException.class, () -> atrService.getCourseAtrReport("invalid-offering-xyz"));
    }

    // =========================================================================
    //  PROGRAMME ATR TESTS
    // =========================================================================

    @Test
    void testGetProgrammeAtrReport_StructureAndOutcomes() {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId).orElseThrow();
        batch.setStatus("COMPLETED");
        programmeBatchRepository.save(batch);

        ProgrammeAtrReportDto report = atrService.getProgrammeAtrReport(masterProgrammeId, programmeBatchId);
        assertNotNull(report);
        assertEquals("PROGRAMME_ATR", report.getReportType());
        assertEquals(masterProgrammeId, report.getProgramme().getId());
        assertEquals(programmeBatchId, report.getBatch().getId());
        assertEquals("DRAFT", report.getStatus());
        assertTrue(report.getIsUnlocked());
        assertNotNull(report.getPoOutcomes());
    }

    @Test
    void testSaveProgrammeAtrReport_Persistence() {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId).orElseThrow();
        batch.setStatus("COMPLETED");
        programmeBatchRepository.save(batch);

        ProgrammeAtrReportDto report = atrService.getProgrammeAtrReport(masterProgrammeId, programmeBatchId);

        if (!report.getPoOutcomes().isEmpty()) {
            report.getPoOutcomes().get(0).setActions(List.of("Curriculum Revision: Introduce cloud DBMS in 5th semester."));
        }

        ProgrammeAtrReportDto saved = atrService.saveProgrammeAtrReport(report);
        assertNotNull(saved);

        // Verify DB persistence
        ProgrammeAtr dbAtr = programmeAtrRepository.findByProgrammeBatchId(programmeBatchId).orElse(null);
        assertNotNull(dbAtr);
        assertNotNull(dbAtr.getObservationsJson());
        assertTrue(dbAtr.getObservationsJson().contains("Introduce cloud DBMS"));

        // Verify that fetching report reloads the persisted actions
        ProgrammeAtrReportDto reloaded = atrService.getProgrammeAtrReport(masterProgrammeId, programmeBatchId);
        assertNotNull(reloaded);
        if (!reloaded.getPoOutcomes().isEmpty()) {
            assertTrue(reloaded.getPoOutcomes().get(0).getActions().contains("Curriculum Revision: Introduce cloud DBMS in 5th semester."));
        }
    }

    @Test
    void testSubmitProgrammeAtr_StatusTransition() {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId).orElseThrow();
        batch.setStatus("COMPLETED");
        programmeBatchRepository.save(batch);

        ProgrammeAtr submitted = atrService.submitProgrammeAtr(masterProgrammeId, programmeBatchId, "Dr. MasterProgramme Coordinator");
        assertNotNull(submitted);
        assertEquals(ProgrammeAtrStatus.SUBMITTED_FOR_VERIFICATION, submitted.getStatus());
        assertEquals("Dr. MasterProgramme Coordinator", submitted.getSubmittedBy());
        assertNotNull(submitted.getSubmittedAt());

        // Verify persisted status in getProgrammeAtrReport
        ProgrammeAtrReportDto report = atrService.getProgrammeAtrReport(masterProgrammeId, programmeBatchId);
        assertEquals("SUBMITTED_FOR_VERIFICATION", report.getStatus());
    }

    @Test
    void testMasterProgrammeAtr_NotFound() {
        assertThrows(ResourceNotFoundException.class, () -> atrService.getProgrammeAtrReport("invalid-prog", programmeBatchId));
        assertThrows(ResourceNotFoundException.class, () -> atrService.getProgrammeAtrReport(masterProgrammeId, "invalid-batch"));
    }

    @Test
    void testProgrammeAtr_LockedWhenBatchIsActive() {
        ProgrammeBatch activeBatch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-active-" + UUID.randomUUID().toString().substring(0, 5))
                .masterProgrammeId(masterProgrammeId)
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .status("ACTIVE")
                .build());

        // Report retrieval indicates locked
        ProgrammeAtrReportDto report = atrService.getProgrammeAtrReport(masterProgrammeId, activeBatch.getId());
        assertNotNull(report);
        assertFalse(report.getIsUnlocked());
        assertEquals("LOCKED_PENDING_COMPLETION", report.getStatus());
        assertTrue(report.getUnlockReason().contains("HOD has not marked this programme batch as COMPLETED or GRADUATED"));

        // Attempt to save -> 409 Conflict
        assertThrows(ResponseStatusException.class, () -> atrService.saveProgrammeAtrReport(report));

        // Attempt to submit -> 409 Conflict
        assertThrows(ResponseStatusException.class, () -> atrService.submitProgrammeAtr(masterProgrammeId, activeBatch.getId(), "Dr. Coordinator"));
    }
}
