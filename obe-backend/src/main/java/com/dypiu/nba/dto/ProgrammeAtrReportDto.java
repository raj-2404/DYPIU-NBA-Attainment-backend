package com.dypiu.nba.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammeAtrReportDto {
    private String reportType;
    private String programmeAtrId;
    private ProgrammeSummary programme;
    private BatchSummary batch;
    private List<OutcomeRow> poOutcomes;
    private List<OutcomeRow> psoOutcomes;
    private String status;
    private Boolean isUnlocked;
    private String unlockReason;
    private String batchStatus;
    private String approvalRequestId;
    private Boolean isSubmittedForReview;
    private Boolean canApprove;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProgrammeSummary {
        private String id;
        private String code;
        private String name;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BatchSummary {
        private String id;
        private String name;
        private String startYear;
        private String endYear;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OutcomeRow {
        private String outcomeCode;
        private String outcomeStatement;
        private BigDecimal targetLevel;
        private BigDecimal attainmentLevel;
        private BigDecimal achievementPercentage;
        private List<String> actions;
    }
}
