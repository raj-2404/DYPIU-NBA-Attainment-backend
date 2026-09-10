package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(
        name = "attainment_configurations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_attainment_config_batch_course",
                        columnNames = {"programme_batch_course_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class AttainmentConfiguration {

    @Id
    private String id;

    @Column(name = "programme_batch_course_id", nullable = false)
    private String programmeBatchCourseId;

    @Column(name = "direct_weight", nullable = false)
    @Builder.Default
    private BigDecimal directWeight = new BigDecimal("80.00");

    @Column(name = "indirect_weight", nullable = false)
    @Builder.Default
    private BigDecimal indirectWeight = new BigDecimal("20.00");

    @Column(name = "direct_threshold", nullable = false)
    @Builder.Default
    private BigDecimal directThreshold = new BigDecimal("60.00");

    @Column(name = "indirect_threshold", nullable = false)
    @Builder.Default
    private BigDecimal indirectThreshold = new BigDecimal("60.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AttainmentConfigStatus status = AttainmentConfigStatus.DRAFT;

    private String submittedBy;

    private ZonedDateTime submittedAt;

    @Column(name = "direct_levels_json", columnDefinition = "TEXT")
    private String directLevelsJson;

    @Column(name = "indirect_levels_json", columnDefinition = "TEXT")
    private String indirectLevelsJson;

    @Column(name = "approved_direct_weight")
    @Builder.Default
    private BigDecimal approvedDirectWeight = new BigDecimal("80.00");

    @Column(name = "approved_indirect_weight")
    @Builder.Default
    private BigDecimal approvedIndirectWeight = new BigDecimal("20.00");

    @Column(name = "approved_direct_threshold")
    @Builder.Default
    private BigDecimal approvedDirectThreshold = new BigDecimal("60.00");

    @Column(name = "approved_indirect_threshold")
    @Builder.Default
    private BigDecimal approvedIndirectThreshold = new BigDecimal("60.00");

    @Column(name = "approved_direct_levels_json", columnDefinition = "TEXT")
    private String approvedDirectLevelsJson;

    @Column(name = "approved_indirect_levels_json", columnDefinition = "TEXT")
    private String approvedIndirectLevelsJson;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Helper compatibility methods
    public String getProgrammeBatchCourseId() {
        return programmeBatchCourseId;
    }

    public void setProgrammeBatchCourseId(String offeringId) {
        this.programmeBatchCourseId = offeringId;
    }

    @Transient
    private String revisionReason;

    @Transient
    private String reviewedBy;

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public BigDecimal getEffectiveApprovedDirectWeight() {
        return approvedDirectWeight != null ? approvedDirectWeight : (directWeight != null ? directWeight : new BigDecimal("80.00"));
    }

    public BigDecimal getEffectiveApprovedIndirectWeight() {
        return approvedIndirectWeight != null ? approvedIndirectWeight : (indirectWeight != null ? indirectWeight : new BigDecimal("20.00"));
    }

    public BigDecimal getEffectiveApprovedDirectThreshold() {
        return approvedDirectThreshold != null ? approvedDirectThreshold : (directThreshold != null ? directThreshold : new BigDecimal("60.00"));
    }

    public BigDecimal getEffectiveApprovedIndirectThreshold() {
        return approvedIndirectThreshold != null ? approvedIndirectThreshold : (indirectThreshold != null ? indirectThreshold : new BigDecimal("60.00"));
    }

    public String getEffectiveApprovedDirectLevelsJson() {
        return (approvedDirectLevelsJson != null && !approvedDirectLevelsJson.isBlank()) ? approvedDirectLevelsJson : directLevelsJson;
    }

    public String getEffectiveApprovedIndirectLevelsJson() {
        return (approvedIndirectLevelsJson != null && !approvedIndirectLevelsJson.isBlank()) ? approvedIndirectLevelsJson : indirectLevelsJson;
    }

    public void snapshotToApproved(String approver) {
        this.approvedDirectWeight = this.directWeight != null ? this.directWeight : new BigDecimal("80.00");
        this.approvedIndirectWeight = this.indirectWeight != null ? this.indirectWeight : new BigDecimal("20.00");
        this.approvedDirectThreshold = this.directThreshold != null ? this.directThreshold : new BigDecimal("60.00");
        this.approvedIndirectThreshold = this.indirectThreshold != null ? this.indirectThreshold : new BigDecimal("60.00");
        this.approvedDirectLevelsJson = this.directLevelsJson;
        this.approvedIndirectLevelsJson = this.indirectLevelsJson;
        this.approvedBy = approver;
        this.approvedAt = ZonedDateTime.now();
    }

    @Transient
    public List<Map<String, Object>> getDirectLevels() {
        if (directLevelsJson != null && !directLevelsJson.isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        directLevelsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
            } catch (Exception ignored) {}
        }
        return List.of(
                Map.of("level", 1, "minPercentage", 0, "maxPercentage", 40),
                Map.of("level", 2, "minPercentage", 40, "maxPercentage", 60),
                Map.of("level", 3, "minPercentage", 60, "maxPercentage", 100)
        );
    }

    public void setDirectLevels(List<Map<String, Object>> directLevels) {
        if (directLevels != null && !directLevels.isEmpty()) {
            try {
                this.directLevelsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(directLevels);
            } catch (Exception ignored) {}
        }
    }

    @Transient
    public List<Map<String, Object>> getApprovedDirectLevels() {
        String json = getEffectiveApprovedDirectLevelsJson();
        if (json != null && !json.isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        json,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
            } catch (Exception ignored) {}
        }
        return List.of(
                Map.of("level", 1, "minPercentage", 0, "maxPercentage", 40),
                Map.of("level", 2, "minPercentage", 40, "maxPercentage", 60),
                Map.of("level", 3, "minPercentage", 60, "maxPercentage", 100)
        );
    }

    public void setApprovedDirectLevels(List<Map<String, Object>> approvedDirectLevels) {
        if (approvedDirectLevels != null && !approvedDirectLevels.isEmpty()) {
            try {
                this.approvedDirectLevelsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(approvedDirectLevels);
            } catch (Exception ignored) {}
        }
    }

    @Transient
    public List<Map<String, Object>> getIndirectLevels() {
        if (indirectLevelsJson != null && !indirectLevelsJson.isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        indirectLevelsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
            } catch (Exception ignored) {}
        }
        return List.of(
                Map.of("level", 1, "minPercentage", 0, "maxPercentage", 40),
                Map.of("level", 2, "minPercentage", 40, "maxPercentage", 60),
                Map.of("level", 3, "minPercentage", 60, "maxPercentage", 100)
        );
    }

    public void setIndirectLevels(List<Map<String, Object>> indirectLevels) {
        if (indirectLevels != null && !indirectLevels.isEmpty()) {
            try {
                this.indirectLevelsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(indirectLevels);
            } catch (Exception ignored) {}
        }
    }

    @Transient
    public List<Map<String, Object>> getApprovedIndirectLevels() {
        String json = getEffectiveApprovedIndirectLevelsJson();
        if (json != null && !json.isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        json,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
            } catch (Exception ignored) {}
        }
        return List.of(
                Map.of("level", 1, "minPercentage", 0, "maxPercentage", 40),
                Map.of("level", 2, "minPercentage", 40, "maxPercentage", 60),
                Map.of("level", 3, "minPercentage", 60, "maxPercentage", 100)
        );
    }

    public void setApprovedIndirectLevels(List<Map<String, Object>> approvedIndirectLevels) {
        if (approvedIndirectLevels != null && !approvedIndirectLevels.isEmpty()) {
            try {
                this.approvedIndirectLevelsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(approvedIndirectLevels);
            } catch (Exception ignored) {}
        }
    }
}
