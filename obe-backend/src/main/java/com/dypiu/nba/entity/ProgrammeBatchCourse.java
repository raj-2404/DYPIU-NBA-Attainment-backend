package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "programme_batch_courses"
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgrammeBatchCourse {

    @Id
    @JsonProperty("programmeBatchCourseId")
    private String id;

    @Transient
    private String masterCourseId;

    @Transient
    private String masterProgrammeId;

    @Column(name = "programme_batch_id", nullable = false)
    private String programmeBatchId;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "credits")
    @Builder.Default
    private Integer credits = 3;

    @Column(name = "course_type", length = 50)
    @Builder.Default
    private String courseType = "THEORY";

    @Column(nullable = false)
    private Integer semester;

    @Column(name = "course_coordinator_id")
    private Long courseCoordinatorId;

    @Column(name = "course_coordinator_name")
    private String courseCoordinatorName;

    @Column(name = "assigned_faculty", columnDefinition = "TEXT")
    private String assignedFaculty;

    @Column(name = "course_code_override", length = 50)
    private String courseCodeOverride;

    @Column(name = "course_name_override", length = 255)
    private String courseNameOverride;

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

    @Transient
    private String coordinatorEmail;

    @Transient
    private String courseCode;

    @Transient
    private String courseName;

    @Transient
    private String academicYear;

    @Transient
    public String getCoordinator() {
        return courseCoordinatorName;
    }

    public void setCoordinator(String coordinator) {
        this.courseCoordinatorName = coordinator;
    }

    public String getCourseCoordinatorEmail() {
        return coordinatorEmail;
    }

    public void setCourseCoordinatorEmail(String courseCoordinatorEmail) {
        this.coordinatorEmail = courseCoordinatorEmail;
    }

    // Helper compatibility methods
    public String getMasterCourseId() {
        return masterCourseId;
    }

    public void setMasterCourseId(String masterCourseId) {
        this.masterCourseId = masterCourseId;
    }

    public String getProgrammeBatchId() {
        return programmeBatchId;
    }

    public void setProgrammeBatchId(String programmeBatchId) {
        this.programmeBatchId = programmeBatchId;
    }

    @JsonProperty("courseCode")
    public String getCourseCode() {
        if (this.courseCodeOverride != null && !this.courseCodeOverride.isBlank()) return this.courseCodeOverride;
        if (this.code != null && !this.code.isBlank()) return this.code;
        return this.courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCode() {
        if (this.code != null && !this.code.isBlank()) return this.code;
        if (this.courseCodeOverride != null && !this.courseCodeOverride.isBlank()) return this.courseCodeOverride;
        return this.courseCode;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonProperty("courseName")
    public String getCourseName() {
        if (this.courseNameOverride != null && !this.courseNameOverride.isBlank()) return this.courseNameOverride;
        if (this.name != null && !this.name.isBlank()) return this.name;
        return this.courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getName() {
        if (this.name != null && !this.name.isBlank()) return this.name;
        if (this.courseNameOverride != null && !this.courseNameOverride.isBlank()) return this.courseNameOverride;
        return this.courseName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEffectiveCourseCode() {
        return getCourseCode();
    }

    public String getEffectiveCourseName() {
        return getCourseName();
    }
}
