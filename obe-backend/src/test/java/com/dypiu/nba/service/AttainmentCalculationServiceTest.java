package com.dypiu.nba.service;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttainmentCalculationServiceTest {

    @Mock
    private AttainmentConfigurationRepository configRepository;

    @Mock
    private StudentCoMarkRepository studentCoMarkRepository;

    @Mock
    private CourseOutcomeRepository courseOutcomeRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ProgrammeBatchRepository programmeBatchRepository;

    @Mock
    private UploadedDocumentRepository uploadedDocumentRepository;
@Mock
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Mock
    private CoPoMappingRepository coPoMappingRepository;

    @Mock
    private CoPsoMappingRepository coPsoMappingRepository;

    @InjectMocks
    private AttainmentCalculationService calculationService;

    private AttainmentConfiguration config;
    private CourseOutcome co1;
    private ProgrammeBatchCourse offering;

    @BeforeEach
    void setUp() {
        offering = ProgrammeBatchCourse.builder()
                .id("offering-1")
                .masterCourseId("crs-1")
                .programmeBatchId("batch-1")
                .semester(5)
                .build();

        config = AttainmentConfiguration.builder()
                .id("cfg-offering-1")
                .programmeBatchCourseId("offering-1")
                .directWeight(new BigDecimal("80.00"))
                .indirectWeight(new BigDecimal("20.00"))
                .directThreshold(new BigDecimal("60.00"))
                .indirectThreshold(new BigDecimal("60.00"))
                .build();

        co1 = CourseOutcome.builder()
                .id("co-1-1")
                .programmeBatchCourseId("offering-1")
                .code("C321.1")
                .statement("Interpret fundamental concepts")
                .build();
    }

    @Test
    void testCalculateCourseCoAttainment_FormulaVerification() {
        when(programmeBatchCourseRepository.existsById("offering-1")).thenReturn(true);
        when(configRepository.findByProgrammeBatchCourseId("offering-1")).thenReturn(Optional.of(config));
        when(courseOutcomeRepository.findByProgrammeBatchCourseId("offering-1")).thenReturn(List.of(co1));

        StudentCoMark m1 = StudentCoMark.builder().coCode("C321.1").marksObtained(new BigDecimal("75")).maxMarks(new BigDecimal("100")).build();
        StudentCoMark m2 = StudentCoMark.builder().coCode("C321.1").marksObtained(new BigDecimal("80")).maxMarks(new BigDecimal("100")).build();
        StudentCoMark m3 = StudentCoMark.builder().coCode("C321.1").marksObtained(new BigDecimal("50")).maxMarks(new BigDecimal("100")).build();

        when(studentCoMarkRepository.findByProgrammeBatchCourseId("offering-1")).thenReturn(List.of(m1, m2, m3));

        Map<String, Object> result = calculationService.calculateCourseCoAttainment("offering-1");

        assertNotNull(result);
        assertTrue(result.containsKey("coAttainments"));
        List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) result.get("coAttainments");
        assertEquals(1, coAttainments.size());

        Map<String, Object> co1Res = coAttainments.get(0);
        assertEquals("C321.1", co1Res.get("coCode"));
        assertNotNull(co1Res.get("combinedAttainment"));
    }
}
