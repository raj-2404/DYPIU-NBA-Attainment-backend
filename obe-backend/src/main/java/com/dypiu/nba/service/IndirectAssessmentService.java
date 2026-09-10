package com.dypiu.nba.service;

import com.dypiu.nba.dto.ConsolidatedIndirectAttainmentDto;
import com.dypiu.nba.dto.IndirectAssessmentDto;
import com.dypiu.nba.entity.ProgrammeBatchIndirectAssessment;
import com.dypiu.nba.exception.BadRequestException;
import com.dypiu.nba.exception.ResourceNotFoundException;
import com.dypiu.nba.repository.ProgrammeBatchIndirectAssessmentRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndirectAssessmentService {

    private final ProgrammeBatchIndirectAssessmentRepository indirectAssessmentRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final BatchLifecycleService batchLifecycleService;

    @Transactional(readOnly = true)
    public List<IndirectAssessmentDto> getAssessments(String programmeBatchId) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new BadRequestException("programmeBatchId is required");
        }
        return indirectAssessmentRepository.findByProgrammeBatchIdOrderByCreatedAtAsc(programmeBatchId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public IndirectAssessmentDto getAssessmentById(String programmeBatchId, String id) {
        return indirectAssessmentRepository.findByIdAndProgrammeBatchId(id, programmeBatchId)
                .map(this::mapToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Indirect assessment not found: " + id));
    }

    @Transactional
    public IndirectAssessmentDto createAssessment(String programmeBatchId, IndirectAssessmentDto dto, String createdBy) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new BadRequestException("programmeBatchId is required");
        }
        if (dto == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        if (dto.getName() == null || dto.getName().trim().isBlank()) {
            throw new BadRequestException("Name/Title is required for the survey or co-curricular event");
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);

        String id = dto.getId();
        if (id == null || id.isBlank()) {
            id = "ind-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String type = (dto.getType() != null && !dto.getType().isBlank()) ? dto.getType().trim().toUpperCase() : "EVENT";

        ProgrammeBatchIndirectAssessment entity = ProgrammeBatchIndirectAssessment.builder()
                .id(id)
                .programmeBatchId(programmeBatchId)
                .name(dto.getName().trim())
                .type(type)
                .description(dto.getDescription())
                .createdBy(createdBy != null ? createdBy : dto.getCreatedBy())
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .build();

        entity.setScores(sanitizeScores(dto.getScores()));
        ProgrammeBatchIndirectAssessment saved = indirectAssessmentRepository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public IndirectAssessmentDto updateAssessment(String programmeBatchId, String id, IndirectAssessmentDto dto, String updatedBy) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new BadRequestException("programmeBatchId is required");
        }
        if (dto == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        if (dto.getName() == null || dto.getName().trim().isBlank()) {
            throw new BadRequestException("Name/Title is required for the survey or co-curricular event");
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);

        ProgrammeBatchIndirectAssessment entity = indirectAssessmentRepository.findByIdAndProgrammeBatchId(id, programmeBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("Indirect assessment not found: " + id));

        entity.setName(dto.getName().trim());
        if (dto.getType() != null && !dto.getType().isBlank()) {
            entity.setType(dto.getType().trim().toUpperCase());
        }
        entity.setDescription(dto.getDescription());
        entity.setScores(sanitizeScores(dto.getScores()));
        entity.setUpdatedAt(ZonedDateTime.now());

        ProgrammeBatchIndirectAssessment updated = indirectAssessmentRepository.save(entity);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteAssessment(String programmeBatchId, String id) {
        batchLifecycleService.enforceBatchEditability(programmeBatchId);
        ProgrammeBatchIndirectAssessment entity = indirectAssessmentRepository.findByIdAndProgrammeBatchId(id, programmeBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("Indirect assessment not found: " + id));
        indirectAssessmentRepository.delete(entity);
    }

    /**
     * Computes the consolidated indirect attainment map across all events, surveys,
     * and the Programme End Survey (if present).
     * Blank/null scores are excluded from both numerator and denominator.
     */
    public Map<String, BigDecimal> computeConsolidatedScores(String programmeBatchId, Map<String, BigDecimal> exitSurveyScores) {
        Map<String, List<BigDecimal>> scoreCollector = new LinkedHashMap<>();

        // 1. Add Exit Survey Scores if present
        if (exitSurveyScores != null) {
            for (Map.Entry<String, BigDecimal> e : exitSurveyScores.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    scoreCollector.computeIfAbsent(e.getKey().toUpperCase(), k -> new ArrayList<>()).add(e.getValue());
                }
            }
        }

        // 2. Add all Event and Stakeholder Survey scores from DB
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            List<ProgrammeBatchIndirectAssessment> assessments = indirectAssessmentRepository.findByProgrammeBatchIdOrderByCreatedAtAsc(programmeBatchId);
            for (ProgrammeBatchIndirectAssessment a : assessments) {
                Map<String, BigDecimal> scores = a.getScores();
                if (scores != null) {
                    for (Map.Entry<String, BigDecimal> e : scores.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) > 0) {
                            scoreCollector.computeIfAbsent(e.getKey().toUpperCase(), k -> new ArrayList<>()).add(e.getValue());
                        }
                    }
                }
            }
        }

        // 3. Compute arithmetic average for each PO/PSO
        Map<String, BigDecimal> consolidated = new LinkedHashMap<>();
        for (Map.Entry<String, List<BigDecimal>> entry : scoreCollector.entrySet()) {
            List<BigDecimal> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal v : values) {
                    sum = sum.add(v);
                }
                BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
                consolidated.put(entry.getKey(), avg);
            }
        }

        return consolidated;
    }

    @Transactional(readOnly = true)
    public ConsolidatedIndirectAttainmentDto getConsolidatedIndirectAttainment(
            String programmeBatchId,
            Map<String, BigDecimal> exitSurveyScores) {

        List<IndirectAssessmentDto> assessments = getAssessments(programmeBatchId);
        Map<String, BigDecimal> consolidated = computeConsolidatedScores(programmeBatchId, exitSurveyScores);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : consolidated.entrySet()) {
            int count = 0;
            if (exitSurveyScores != null && exitSurveyScores.containsKey(e.getKey()) && exitSurveyScores.get(e.getKey()).compareTo(BigDecimal.ZERO) > 0) {
                count++;
            }
            for (IndirectAssessmentDto a : assessments) {
                if (a.getScores() != null && a.getScores().containsKey(e.getKey()) && a.getScores().get(e.getKey()).compareTo(BigDecimal.ZERO) > 0) {
                    count++;
                }
            }
            counts.put(e.getKey(), count);
        }

        return ConsolidatedIndirectAttainmentDto.builder()
                .programmeBatchId(programmeBatchId)
                .assessments(assessments)
                .programmeEndSurveyScores(exitSurveyScores != null ? exitSurveyScores : Collections.emptyMap())
                .consolidatedIndirectAttainment(consolidated)
                .evaluationCounts(counts)
                .build();
    }

    private Map<String, BigDecimal> sanitizeScores(Map<String, BigDecimal> raw) {
        if (raw == null) return Collections.emptyMap();
        Map<String, BigDecimal> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : raw.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                BigDecimal val = e.getValue();
                if (val.compareTo(BigDecimal.ZERO) > 0) {
                    // Clamp between 0.00 and 3.00
                    if (val.compareTo(new BigDecimal("3.00")) > 0) val = new BigDecimal("3.00");
                    sanitized.put(e.getKey().trim().toUpperCase(), val.setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return sanitized;
    }

    private IndirectAssessmentDto mapToDto(ProgrammeBatchIndirectAssessment entity) {
        return IndirectAssessmentDto.builder()
                .id(entity.getId())
                .programmeBatchId(entity.getProgrammeBatchId())
                .name(entity.getName())
                .type(entity.getType())
                .description(entity.getDescription())
                .scores(entity.getScores())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
