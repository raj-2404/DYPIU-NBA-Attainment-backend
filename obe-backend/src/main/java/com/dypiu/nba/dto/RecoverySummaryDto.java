package com.dypiu.nba.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoverySummaryDto {
    private long totalDeleted;
    private long programmesCount;
    private long batchesCount;
    private long coursesCount;
    private long usersCount;
}
