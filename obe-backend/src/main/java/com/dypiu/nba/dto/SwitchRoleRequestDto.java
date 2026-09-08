package com.dypiu.nba.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchRoleRequestDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Role is required")
    @JsonAlias({"targetRole", "roleCode"})
    private String role;

    @JsonAlias({"contextId"})
    private String departmentId;

    private String schoolId;

    private String masterProgrammeId;

    private String programmeBatchId;
}
