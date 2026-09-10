package com.dypiu.nba.service;

import com.dypiu.nba.dto.ConsolidatedIndirectAttainmentDto;
import com.dypiu.nba.dto.IndirectAssessmentDto;
import com.dypiu.nba.entity.ProgrammeBatchIndirectAssessment;
import com.dypiu.nba.repository.ProgrammeBatchIndirectAssessmentRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndirectAssessmentServiceTest {

    @Mock
    private ProgrammeBatchIndirectAssessmentRepository repository;

    @Mock
    private ProgrammeBatchRepository programmeBatchRepository;

    @Mock
    private BatchLifecycleService batchLifecycleService;

    private IndirectAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new IndirectAssessmentService(repository, programmeBatchRepository, batchLifecycleService);
    }

    @Test
    void testConsolidateIndirectAttainment_WithAssessmentsAndProgrammeEndSurvey() {
        String batchId = "batch-101";

        ProgrammeBatchIndirectAssessment assessment1 = ProgrammeBatchIndirectAssessment.builder()
                .id("ass-1")
                .programmeBatchId(batchId)
                .name("Hackathon 2026")
                .type("EVENT")
                .build();
        assessment1.setScores(Map.of(
                "PO1", new BigDecimal("2.40"),
                "PO2", new BigDecimal("2.80"),
                "PSO1", new BigDecimal("3.00")
        ));

        when(repository.findByProgrammeBatchIdOrderByCreatedAtAsc(batchId))
                .thenReturn(List.of(assessment1));

        Map<String, BigDecimal> progEndSurveyScores = Map.of(
                "PO1", new BigDecimal("2.60"),
                "PO2", new BigDecimal("2.20"),
                "PO3", new BigDecimal("2.50")
        );

        ConsolidatedIndirectAttainmentDto consolidated = service.getConsolidatedIndirectAttainment(batchId, progEndSurveyScores);

        assertNotNull(consolidated);
        Map<String, BigDecimal> scores = consolidated.getConsolidatedIndirectAttainment();
        assertNotNull(scores);

        // PO1: average of 2.40 and 2.60 = 2.50
        assertEquals(new BigDecimal("2.50"), scores.get("PO1"));
        // PO2: average of 2.80 and 2.20 = 2.50
        assertEquals(new BigDecimal("2.50"), scores.get("PO2"));
        // PO3: only from survey = 2.50
        assertEquals(new BigDecimal("2.50"), scores.get("PO3"));
        // PSO1: only from event = 3.00
        assertEquals(new BigDecimal("3.00"), scores.get("PSO1"));

        // Evaluation counts
        assertEquals(2, consolidated.getEvaluationCounts().get("PO1"));
        assertEquals(2, consolidated.getEvaluationCounts().get("PO2"));
        assertEquals(1, consolidated.getEvaluationCounts().get("PO3"));
        assertEquals(1, consolidated.getEvaluationCounts().get("PSO1"));
    }

    @Test
    void testConsolidateIndirectAttainment_WhenNoAssessments_ReturnsSurveyScores() {
        String batchId = "batch-102";
        when(repository.findByProgrammeBatchIdOrderByCreatedAtAsc(batchId))
                .thenReturn(Collections.emptyList());

        Map<String, BigDecimal> progEndSurveyScores = Map.of("PO1", new BigDecimal("2.75"));

        ConsolidatedIndirectAttainmentDto consolidated = service.getConsolidatedIndirectAttainment(batchId, progEndSurveyScores);

        assertEquals(1, consolidated.getConsolidatedIndirectAttainment().size());
        assertEquals(new BigDecimal("2.75"), consolidated.getConsolidatedIndirectAttainment().get("PO1"));
    }

    @Test
    void testCreateIndirectAssessment() {
        String batchId = "batch-103";

        IndirectAssessmentDto dto = IndirectAssessmentDto.builder()
                .name("Technical Workshop")
                .type("EVENT")
                .description("Hands-on coding workshop")
                .scores(Map.of("PO1", new BigDecimal("2.90"), "PSO1", new BigDecimal("2.50")))
                .build();

        when(repository.save(any())).thenAnswer(inv -> {
            ProgrammeBatchIndirectAssessment entity = inv.getArgument(0);
            return entity;
        });

        IndirectAssessmentDto result = service.createAssessment(batchId, dto, "test-user");

        assertNotNull(result);
        assertEquals("Technical Workshop", result.getName());
        assertEquals(new BigDecimal("2.90"), result.getScores().get("PO1"));
        assertEquals(new BigDecimal("2.50"), result.getScores().get("PSO1"));
        verify(repository, times(1)).save(any());
        verify(batchLifecycleService, times(1)).enforceBatchEditability(batchId);
    }
}
