package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "master_programmes"
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@Builder
public class MasterProgramme {

    public static final Map<String, String[]> COORDINATOR_CACHE = new ConcurrentHashMap<>();

    @Id
    @JsonProperty("masterProgrammeId")
    private String id;

    @Column(name = "department_id", nullable = false)
    private String departmentId;

    @Column(name = "degree_awarded", nullable = false, length = 100)
    @JsonProperty("degreeAwarded")
    @com.fasterxml.jackson.annotation.JsonAlias({"code", "degree_awarded", "degreeAwarded"})
    private String degreeAwarded;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "duration_years", nullable = false)
    @Builder.Default
    private Integer durationYears = 4;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Builder.Default
    @Column(name = "level", length = 20)
    private String level = "UG";

    @Column(name = "department_name", length = 255)
    private String departmentName;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    @Transient
    private String coordinator;

    @Transient
    private String coordinatorEmail;

    public MasterProgramme(String id, String departmentId, String degreeAwarded, String name, Integer durationYears, String status, String level, String departmentName, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt, String deletedBy, String coordinator, String coordinatorEmail) {
        this.id = id;
        this.departmentId = departmentId;
        this.degreeAwarded = degreeAwarded;
        this.name = name;
        this.durationYears = durationYears != null ? durationYears : 4;
        this.status = status != null ? status : "ACTIVE";
        this.level = (level != null && !level.isBlank()) ? level : "UG";
        this.departmentName = departmentName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.coordinator = coordinator;
        this.coordinatorEmail = coordinatorEmail;
        if (id != null && coordinator != null && !coordinator.isBlank()) {
            COORDINATOR_CACHE.put(id, new String[]{coordinator, coordinatorEmail != null ? coordinatorEmail : ""});
        }
    }

    public MasterProgramme(String id, String departmentId, String degreeAwarded, String name, Integer durationYears, String status, String departmentName, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt, String deletedBy, String coordinator, String coordinatorEmail) {
        this(id, departmentId, degreeAwarded, name, durationYears, status, "UG", departmentName, createdAt, updatedAt, deletedAt, deletedBy, coordinator, coordinatorEmail);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCode() {
        return this.degreeAwarded;
    }

    public void setCode(String code) {
        this.degreeAwarded = code;
    }

    public String getCoordinator() {
        if (this.coordinator != null && !this.coordinator.isBlank() && !"Not Assigned".equalsIgnoreCase(this.coordinator)) {
            if (this.id != null) {
                COORDINATOR_CACHE.put(this.id, new String[]{this.coordinator, this.coordinatorEmail != null ? this.coordinatorEmail : ""});
            }
            return this.coordinator;
        }
        if (this.id != null && COORDINATOR_CACHE.containsKey(this.id)) {
            String[] pair = COORDINATOR_CACHE.get(this.id);
            if (pair != null && pair.length > 0 && pair[0] != null && !pair[0].isBlank()) {
                return pair[0];
            }
        }
        return this.coordinator;
    }

    public String getCoordinatorEmail() {
        if (this.coordinatorEmail != null && !this.coordinatorEmail.isBlank()) {
            if (this.id != null) {
                COORDINATOR_CACHE.put(this.id, new String[]{this.coordinator != null ? this.coordinator : "", this.coordinatorEmail});
            }
            return this.coordinatorEmail;
        }
        if (this.id != null && COORDINATOR_CACHE.containsKey(this.id)) {
            String[] pair = COORDINATOR_CACHE.get(this.id);
            if (pair != null && pair.length > 1 && pair[1] != null && !pair[1].isBlank()) {
                return pair[1];
            }
        }
        return this.coordinatorEmail;
    }

    public void setCoordinator(String coordinator) {
        this.coordinator = coordinator;
        if (this.id != null && coordinator != null && !coordinator.isBlank()) {
            COORDINATOR_CACHE.put(this.id, new String[]{coordinator, this.coordinatorEmail != null ? this.coordinatorEmail : ""});
        }
    }

    public void setCoordinatorEmail(String coordinatorEmail) {
        this.coordinatorEmail = coordinatorEmail;
        if (this.id != null && coordinatorEmail != null && !coordinatorEmail.isBlank()) {
            String c = this.coordinator != null ? this.coordinator : "";
            COORDINATOR_CACHE.put(this.id, new String[]{c, coordinatorEmail});
        }
    }

    public static class MasterProgrammeBuilder {
        public MasterProgrammeBuilder code(String code) {
            this.degreeAwarded = code;
            return this;
        }
    }
}

