package com.dypiu.nba.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /*
     * Organizational scope.
     *
     * IQAC      → schoolId may be null because IQAC is institution-wide
     * DIRECTOR  → schoolId
     * HOD       → departmentId
     * PC        → masterProgrammeId
     * FACULTY   → depends on assignment
     */
    @Column(name = "school_id")
    private String schoolId;

    @Column(name = "department_id")
    private String departmentId;

    @Column(name = "master_programme_id")
    private String masterProgrammeId;

    @Column(name = "assigned_roles", columnDefinition = "TEXT")
    private String assignedRoles;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private ZonedDateTime updatedAt;

    @Transient
    private String department;

    @Transient
    private String programme;

    public java.util.List<String> getExplicitAssignedRoles() {
        if (assignedRoles != null && !assignedRoles.isBlank()) {
            try {
                String trimmed = assignedRoles.trim();
                if (trimmed.startsWith("[")) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                } else {
                    return java.util.Arrays.stream(trimmed.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList());
                }
            } catch (Exception ignored) {}
        }
        return java.util.Collections.emptyList();
    }

    public java.util.List<String> getRoleList() {
        java.util.List<String> explicit = getExplicitAssignedRoles();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return role != null ? java.util.List.of(role.name()) : java.util.Collections.emptyList();
    }

    public void setRoleList(java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            this.assignedRoles = null;
        } else {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                this.assignedRoles = mapper.writeValueAsString(roles);
            } catch (Exception e) {
                this.assignedRoles = String.join(",", roles);
            }
        }
    }
}