package com.dypiu.nba.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsolidatedIndirectAttainmentDto {

    private String programmeBatchId;
    private List<IndirectAssessmentDto> assessments; // All events and surveys
    private Map<String, BigDecimal> programmeEndSurveyScores; // From Tab 2 (Exit Survey)
    private Map<String, BigDecimal> consolidatedIndirectAttainment; // Live consolidated average per PO and PSO
    private Map<String, Integer> evaluationCounts; // Count of valid evaluation sources per PO/PSO
}
