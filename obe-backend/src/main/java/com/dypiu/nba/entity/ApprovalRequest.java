package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @com.fasterxml.jackson.annotation.JsonProperty("approvalRequestId")
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApprovalType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "school_id")
    private String schoolId;

    @Column(name = "department_id")
    private String departmentId;

    @Column(name = "master_programme_id")
    private String masterProgrammeId;

    @Column(name = "programme_batch_id")
    private String programmeBatchId;

    @Transient
    private String masterCourseId;

    @Column(name = "programme_batch_course_id")
    private String programmeBatchCourseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "submitted_at")
    private ZonedDateTime submittedAt;

    private String approvedBy;

    private ZonedDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    // Helper compatibility methods
    public String getMasterProgrammeId() {
        return masterProgrammeId;
    }

    public void setMasterProgrammeId(String masterProgrammeId) {
        this.masterProgrammeId = masterProgrammeId;
    }

    public String getProgrammeBatchId() {
        return programmeBatchId;
    }

    public void setProgrammeBatchId(String programmeBatchId) {
        this.programmeBatchId = programmeBatchId;
    }

    public String getMasterCourseId() {
        return masterCourseId;
    }

    public void setMasterCourseId(String masterCourseId) {
        this.masterCourseId = masterCourseId;
    }

    public String getProgrammeBatchCourseId() {
        return programmeBatchCourseId;
    }

    public void setProgrammeBatchCourseId(String offeringId) {
        this.programmeBatchCourseId = offeringId;
    }
}
