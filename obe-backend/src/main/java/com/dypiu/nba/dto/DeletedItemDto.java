package com.dypiu.nba.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletedItemDto {
    private String id;
    private String resourceType; // "PROGRAMME", "BATCH", "COURSE", "USER"
    private String name;
    private String code;
    private String category;
    private String status;
    private ZonedDateTime deletedAt;
    private String deletedBy;
    private String parentInfo;
    private boolean canRestore;
    private String warningMessage;
    private Map<String, Object> details;
}
