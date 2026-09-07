package com.dypiu.nba.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseOfferingRequestDto {

    private String id;
    private String programmeBatchCourseId;
    private String programmeBatchId;
    private String masterCourseId;
    private String code;
    private String name;
    private String courseCodeOverride;
    private String courseNameOverride;
    private Integer credits;
    private String courseType;
    private Integer semester;
    private String academicYear;
    private Long courseCoordinatorId;
    private String courseCoordinatorName;
    private String courseCoordinatorEmail;
    private String coordinatorEmail;
    private String coordinator;
    private Object assignedFaculty;

    public String getEffectiveCode() {
        if (code != null && !code.isBlank()) return code.trim();
        if (courseCodeOverride != null && !courseCodeOverride.isBlank()) return courseCodeOverride.trim();
        return null;
    }

    public String getEffectiveName() {
        if (name != null && !name.isBlank()) return name.trim();
        if (courseNameOverride != null && !courseNameOverride.isBlank()) return courseNameOverride.trim();
        return null;
    }
}

