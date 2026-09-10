package com.dypiu.nba.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndirectAssessmentDto {

    private String id;
    private String programmeBatchId;
    private String name;
    private String type; // "EVENT" or "SURVEY"
    private String description;
    private Map<String, BigDecimal> scores; // {"PO1": 2.50, "PO2": 3.00, ...}
    private String createdBy;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
