package com.dypiu.nba.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private Long id;
    private String username;
    private String name;
    private String email;
    private String role;
    private String schoolId;
    private String departmentId;
    private String masterProgrammeId;
    private String department;
    private String programme;
    private java.util.List<String> roles;
}
