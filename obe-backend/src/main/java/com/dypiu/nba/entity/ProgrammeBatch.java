package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "programme_batches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_programme_batch_start_year",
                        columnNames = {"master_programme_id", "start_year"}
                )
        }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgrammeBatch {

    @Id
    @JsonProperty("programmeBatchId")
    private String id;

    @Column(name = "master_programme_id", nullable = false)
    private String masterProgrammeId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "start_year", nullable = false)
    private Integer startYear;

    @Column(name = "end_year", nullable = false)
    private Integer endYear;

    @Column(name = "duration_years", nullable = false)
    @Builder.Default
    private Integer durationYears = 4;

    @Column(name = "coordinator_id")
    private Long coordinatorId;

    @Column(name = "coordinator_name", length = 150)
    private String coordinatorName;

    @Column(name = "coordinator_email", length = 150)
    private String coordinatorEmail;

    @Column(name = "year_level", length = 100)
    private String yearLevel;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private ZonedDateTime updatedAt;

    // Batch Lifecycle and Reopening Window
    @Column(name = "editing_window_until")
    private ZonedDateTime editingWindowUntil;

    @Column(name = "editing_window_opened_at")
    private ZonedDateTime editingWindowOpenedAt;

    @Column(name = "editing_window_opened_by")
    private String editingWindowOpenedBy;

    @Transient
    private String programmeName;

    @Transient
    private String programmeCode;

    @Transient
    @Builder.Default
    private Integer currentSemester = 1;

    // Helper getter/setter for compatibility during phased migration
    public String getMasterProgrammeId() {
        return masterProgrammeId;
    }

    public void setMasterProgrammeId(String masterProgrammeId) {
        this.masterProgrammeId = masterProgrammeId;
    }

    public String getCoordinator() {
        return coordinatorName;
    }

    public void setCoordinator(String coordinator) {
        this.coordinatorName = coordinator;
    }
}
