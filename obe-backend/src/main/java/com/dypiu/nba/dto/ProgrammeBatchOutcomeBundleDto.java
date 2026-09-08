package com.dypiu.nba.dto;

import com.dypiu.nba.entity.PeoOutcome;
import com.dypiu.nba.entity.ProgrammeOutcome;
import com.dypiu.nba.entity.ProgrammeSpecificOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgrammeBatchOutcomeBundleDto {
    private String programmeBatchId;
    private String masterProgrammeId;
    private List<ProgrammeOutcome> pos;
    private List<ProgrammeSpecificOutcome> psos;
    private List<PeoOutcome> peos;
    private Map<String, BigDecimal> poTargets;
    private Map<String, BigDecimal> psoTargets;
    private String status;
    private String approvalRequestId;
    private Boolean isSubmittedForReview;
    private Boolean canApprove;
}
