package com.dypiu.nba.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String email;
    private String name;
    private String primaryRole;
    private String activeRole;
    private List<UserProfileRoleDto> profiles;
}
