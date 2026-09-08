package com.dypiu.nba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileRoleDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary role identifier: IQAC, DIRECTOR, HOD, PROGRAMME_COORDINATOR, COURSE_COORDINATOR, FACULTY
     */
    private String role;

    /**
     * Standardized role code (same as role)
     */
    private String roleCode;

    /**
     * Human-readable label for UI displays (e.g., "Head of Department - Computer Engineering")
     */
    private String displayName;

    /**
     * Title for quick display
     */
    private String title;

    /**
     * Brief explanation of permissions under this profile
     */
    private String description;

    /**
     * Assigned course count (for Course Coordinator profile)
     */
    private Integer assignedCoursesCount;

    /**
     * Scoped entity IDs & Names
     */
    private String schoolId;
    private String schoolName;

    private String departmentId;
    private String departmentName;

    private String masterProgrammeId;
    private String masterProgrammeName;

    private String programmeBatchId;
    private String programmeBatchName;

    /**
     * True if this is the active profile for the current token/session
     */
    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("isCurrent")
    private boolean isCurrent;
}
