package com.dypiu.nba.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

@Entity
@Table(
        name = "programme_batch_indirect_assessments",
        indexes = {
                @Index(name = "idx_pbia_batch_id", columnList = "programme_batch_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProgrammeBatchIndirectAssessment {

    @Id
    private String id;

    @Column(name = "programme_batch_id", nullable = false)
    private String programmeBatchId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String type = "EVENT"; // "EVENT", "SURVEY", "CO_CURRICULAR", etc.

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "scores_json", columnDefinition = "TEXT")
    private String scoresJson;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (updatedAt == null) updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    @Transient
    public Map<String, BigDecimal> getScores() {
        if (scoresJson == null || scoresJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return new ObjectMapper().readValue(scoresJson, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public void setScores(Map<String, BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            this.scoresJson = null;
        } else {
            try {
                this.scoresJson = new ObjectMapper().writeValueAsString(scores);
            } catch (Exception e) {
                this.scoresJson = null;
            }
        }
    }
}
