package com.dypiu.nba.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterReadinessDto {

    private String programmeBatchId;
    private String batchName;
    private Integer semester;
    private String status;
    private Boolean isCompleted;
    
    @Builder.Default
    private Boolean canComplete = true;
    
    private Integer courseCount;
    private Integer readyCourseCount;

    @Builder.Default
    private List<ReadinessWarning> warnings = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadinessWarning {
        private String programmeBatchCourseId;
        private String courseCode;
        private String courseName;
        private String issue;
        private String message;
    }
}
