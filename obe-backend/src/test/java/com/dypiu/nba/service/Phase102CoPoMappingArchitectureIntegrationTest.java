package com.dypiu.nba.service;

import com.dypiu.nba.dto.CourseAttainmentReportDto;
import com.dypiu.nba.dto.CourseMappingMatrixDto;
import com.dypiu.nba.dto.ProgrammeBatchAttainmentReportDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class Phase102CoPoMappingArchitectureIntegrationTest {

    @Autowired
    private OutcomeService outcomeService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private AttainmentCalculationService calculationService;

    @Autowired
    private AttainmentReportService attainmentReportService;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SchoolRepository schoolRepository;

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
    private CourseMappingKeywordRepository courseMappingKeywordRepository;

    @MockBean
    private CurrentUserScopeService currentUserScopeService;

    private School school;
    private Department dept;
    private MasterProgramme prog;
    private ProgrammeBatch batch2024;
    private ProgrammeBatch batch2028;
    private ProgrammeBatchCourse masterCourse;
    private ProgrammeBatchCourse offering2024;
    private ProgrammeBatchCourse offering2028;
    private CourseOutcome co2024_1;
    private CourseOutcome co2024_2;
    private CourseOutcome co2028_1;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 6);

        school = schoolRepository.save(School.builder()
                .id("sch-map-" + uid)
                .name("Engineering School")
                .code("ENG-MAP-" + uid)
                .build());

        dept = departmentRepository.save(Department.builder()
                .id("dept-map-" + uid)
                .name("Computer Science")
                .code("CS-MAP-" + uid)
                .schoolId(school.getId())
                .build());

        prog = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-map-" + uid)
                .name("B.Tech CSE")
                .code("BTCSE-" + uid)
                .departmentId(dept.getId())
                .durationYears(4)
                .build());

        batch2024 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Batch 2024-2028")
                .startYear(2024)
                .endYear(2028)
                .status("ACTIVE")
                .build());

        batch2028 = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2028-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Batch 2028-2032")
                .startYear(2028)
                .endYear(2032)
                .status("ACTIVE")
                .build());

        masterCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch2024.getId()).semester(1)
                .id("mc-os-" + uid)
                .masterProgrammeId(prog.getId())
                .name("Operating Systems")
                .code("CS301")
                .credits(4)
                .build());

        offering2024 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-os-24-" + uid)
                .masterCourseId(masterCourse.getId())
                .programmeBatchId(batch2024.getId())
                .semester(4)
                .courseCoordinatorId(101L)
                .courseCoordinatorName("Dr. Kernel")
                .build());

        offering2028 = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-os-28-" + uid)
                .masterCourseId(masterCourse.getId())
                .programmeBatchId(batch2028.getId())
                .semester(4)
                .courseCoordinatorId(202L)
                .courseCoordinatorName("Dr. ModernOS")
                .build());

        co2024_1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-24-1-" + uid)
                .programmeBatchCourseId(offering2024.getId())
                .code("CO1")
                .statement("Explain Process Scheduling")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        co2024_2 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-24-2-" + uid)
                .programmeBatchCourseId(offering2024.getId())
                .code("CO2")
                .statement("Manage Virtual Memory")
                .targetLevel(new BigDecimal("2.50"))
                .build());

        co2028_1 = courseOutcomeRepository.save(CourseOutcome.builder()
                .id("co-28-1-" + uid)
                .programmeBatchCourseId(offering2028.getId())
                .code("CO1")
                .statement("Analyze Multicore Scheduling")
                .targetLevel(new BigDecimal("2.60"))
                .build());

        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po-map-1-" + uid)
                .programmeBatchId(batch2024.getId())
                .code("PO1")
                .statement("Engineering Knowledge")
                .target(new BigDecimal("2.50"))
                .build());

        programmeOutcomeRepository.save(ProgrammeOutcome.builder()
                .id("po-map-2-" + uid)
                .programmeBatchId(batch2024.getId())
                .code("PO2")
                .statement("Problem Analysis")
                .target(new BigDecimal("2.50"))
                .build());

        programmeSpecificOutcomeRepository.save(ProgrammeSpecificOutcome.builder()
                .id("pso-map-1-" + uid)
                .programmeBatchId(batch2024.getId())
                .code("PSO1")
                .statement("Systems Programming")
                .target(new BigDecimal("2.50"))
                .build());
    }

    @Test
    @DisplayName("Phase 10.2: CO mappings and keywords are batch-specific and independent across offerings of same MasterCourse")
    void testBatchSpecificCoMappingIndependence() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Map 2024 offering COs: CO1 -> PO1:3, PO2:2, PSO1:3; CO2 -> PO1:2, PO2:3, PSO1:1
        CourseMappingMatrixDto dto2024 = CourseMappingMatrixDto.builder()
                .masterCourseId(offering2024.getId())
                .poMappings(List.of(
                        CoPoMapping.builder().courseOutcomeId(co2024_1.getId()).poCode("PO1").mappingLevel(3).build(),
                        CoPoMapping.builder().courseOutcomeId(co2024_1.getId()).poCode("PO2").mappingLevel(2).build(),
                        CoPoMapping.builder().courseOutcomeId(co2024_2.getId()).poCode("PO1").mappingLevel(2).build(),
                        CoPoMapping.builder().courseOutcomeId(co2024_2.getId()).poCode("PO2").mappingLevel(3).build()
                ))
                .psoMappings(List.of(
                        CoPsoMapping.builder().courseOutcomeId(co2024_1.getId()).psoCode("PSO1").mappingLevel(3).build(),
                        CoPsoMapping.builder().courseOutcomeId(co2024_2.getId()).psoCode("PSO1").mappingLevel(1).build()
                ))
                .poKeywordsStore(Map.of("PO1", List.of("Knowledge", "Scheduling"), "PO2", List.of("Analysis")))
                .psoKeywordsStore(Map.of("PSO1", List.of("OS Architecture")))
                .build();

        outcomeService.saveCourseMappings(offering2024.getId(), dto2024);

        // Map 2028 offering CO1: CO1 -> PO1:1, PO2:1, PSO1:2
        CourseMappingMatrixDto dto2028 = CourseMappingMatrixDto.builder()
                .masterCourseId(offering2028.getId())
                .poMappings(List.of(
                        CoPoMapping.builder().courseOutcomeId(co2028_1.getId()).poCode("PO1").mappingLevel(1).build(),
                        CoPoMapping.builder().courseOutcomeId(co2028_1.getId()).poCode("PO2").mappingLevel(1).build()
                ))
                .psoMappings(List.of(
                        CoPsoMapping.builder().courseOutcomeId(co2028_1.getId()).psoCode("PSO1").mappingLevel(2).build()
                ))
                .poKeywordsStore(Map.of("PO1", List.of("Multicore"), "PO2", List.of("Evaluation")))
                .psoKeywordsStore(Map.of("PSO1", List.of("Modern Systems")))
                .build();

        outcomeService.saveCourseMappings(offering2028.getId(), dto2028);

        // Verify 2024 mappings
        CourseMappingMatrixDto retrieved2024 = outcomeService.getCourseMappings(offering2024.getId());
        assertNotNull(retrieved2024);
        assertEquals(3, retrieved2024.getMatrix().get("CO1").get("PO1"));
        assertEquals(2, retrieved2024.getMatrix().get("CO1").get("PO2"));
        assertEquals(3, retrieved2024.getMatrix().get("CO1").get("PSO1"));
        assertEquals(2, retrieved2024.getMatrix().get("CO2").get("PO1"));
        assertEquals(3, retrieved2024.getMatrix().get("CO2").get("PO2"));
        assertEquals(1, retrieved2024.getMatrix().get("CO2").get("PSO1"));

        // Averages for 2024: PO1 = (3+2)/2 = 2.50; PO2 = (2+3)/2 = 2.50; PSO1 = (3+1)/2 = 2.00
        assertEquals(new BigDecimal("2.50"), retrieved2024.getPoAverages().get("PO1"));
        assertEquals(new BigDecimal("2.50"), retrieved2024.getPoAverages().get("PO2"));
        assertEquals(new BigDecimal("2.00"), retrieved2024.getPsoAverages().get("PSO1"));

        // Verify 2028 mappings are completely independent
        CourseMappingMatrixDto retrieved2028 = outcomeService.getCourseMappings(offering2028.getId());
        assertNotNull(retrieved2028);
        assertEquals(1, retrieved2028.getMatrix().get("CO1").get("PO1"));
        assertEquals(new BigDecimal("1.00"), retrieved2028.getPoAverages().get("PO1"));
    }

    @Test
    @DisplayName("Phase 10.2: Course Attainment Report Table 1, Table 2 & Programme Reports correctly consume authoritative mappings")
    void testAttainmentReportsConsumeAuthoritativeMappings() {
        CurrentUserScope iqacScope = CurrentUserScope.builder()
                .role(UserRole.IQAC)
                .build();
        when(currentUserScopeService.getCurrentUserScope()).thenReturn(iqacScope);

        // Save mappings for 2024 offering
        CourseMappingMatrixDto dto = CourseMappingMatrixDto.builder()
                .masterCourseId(offering2024.getId())
                .poMappings(List.of(
                        CoPoMapping.builder().courseOutcomeId(co2024_1.getId()).poCode("PO1").mappingLevel(3).build(),
                        CoPoMapping.builder().courseOutcomeId(co2024_2.getId()).poCode("PO1").mappingLevel(3).build()
                ))
                .psoMappings(List.of(
                        CoPsoMapping.builder().courseOutcomeId(co2024_1.getId()).psoCode("PSO1").mappingLevel(3).build()
                ))
                .build();
        outcomeService.saveCourseMappings(offering2024.getId(), dto);

        // Generate Course Attainment Report
        CourseAttainmentReportDto courseReport = attainmentReportService.getOrCreateCourseAttainmentReport(offering2024.getId());

        assertNotNull(courseReport);
        assertNotNull(courseReport.getTable1Mapping());
        assertFalse(courseReport.getTable1Mapping().isEmpty());

        // Verify Table 2 has PO1 direct contribution
        assertNotNull(courseReport.getTable2DirectPO());
        boolean hasPo1 = courseReport.getTable2DirectPO().stream().anyMatch(r -> "PO1".equals(r.getPoCode()));
        assertTrue(hasPo1, "Table 2 must contain PO1 direct contribution calculated from average mapping");

        // Generate Programme Batch Attainment Report
        ProgrammeBatchAttainmentReportDto progReport = attainmentReportService.getOrCreateProgrammeAttainmentReport(prog.getId(), batch2024.getId());

        assertNotNull(progReport);
        assertNotNull(progReport.getReport1AverageMappingPO());
        assertFalse(progReport.getReport1AverageMappingPO().isEmpty());
    }
}
