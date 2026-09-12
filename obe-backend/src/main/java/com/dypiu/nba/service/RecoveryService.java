package com.dypiu.nba.service;

import com.dypiu.nba.audit.AuditAction;
import com.dypiu.nba.audit.ResourceType;
import com.dypiu.nba.dto.DeletedItemDto;
import com.dypiu.nba.dto.RecoverySummaryDto;
import com.dypiu.nba.entity.MasterProgramme;
import com.dypiu.nba.entity.ProgrammeBatch;
import com.dypiu.nba.entity.ProgrammeBatchCourse;
import com.dypiu.nba.entity.User;
import com.dypiu.nba.repository.MasterProgrammeRepository;
import com.dypiu.nba.repository.ProgrammeBatchCourseRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import com.dypiu.nba.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryService {

    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final UserRepository userRepository;
    private final AcademicLookupCacheService academicLookupCacheService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<DeletedItemDto> getDeletedItems(String resourceType, String search, ZonedDateTime from, ZonedDateTime to) {
        List<DeletedItemDto> items = new ArrayList<>();
        String filterType = (resourceType != null && !resourceType.isBlank()) ? resourceType.trim().toUpperCase() : "ALL";

        // 1. Programmes
        if ("ALL".equals(filterType) || "PROGRAMME".equals(filterType) || "MASTER_PROGRAMME".equals(filterType)) {
            List<MasterProgramme> trashProgrammes = masterProgrammeRepository.findAllTrashProgrammes();
            for (MasterProgramme p : trashProgrammes) {
                Map<String, Object> details = new HashMap<>();
                details.put("departmentId", p.getDepartmentId());
                details.put("durationYears", p.getDurationYears());
                details.put("level", p.getLevel());

                items.add(DeletedItemDto.builder()
                        .id(p.getId())
                        .resourceType("PROGRAMME")
                        .name(p.getName())
                        .code(p.getDegreeAwarded())
                        .category("Academic Programme")
                        .status(p.getStatus())
                        .deletedAt(p.getDeletedAt())
                        .deletedBy(p.getDeletedBy() != null ? p.getDeletedBy() : "Administrator")
                        .parentInfo("Dept: " + (p.getDepartmentName() != null ? p.getDepartmentName() : p.getDepartmentId()))
                        .canRestore(true)
                        .warningMessage(null)
                        .details(details)
                        .build());
            }
        }

        // 2. Batches
        if ("ALL".equals(filterType) || "BATCH".equals(filterType) || "PROGRAMME_BATCH".equals(filterType)) {
            List<ProgrammeBatch> trashBatches = programmeBatchRepository.findAllTrashBatches();
            for (ProgrammeBatch b : trashBatches) {
                Optional<MasterProgramme> parentProg = masterProgrammeRepository.findByIdAndDeletedAtIsNull(b.getMasterProgrammeId());
                boolean canRestore = parentProg.isPresent();
                String warning = canRestore ? null : "Parent Programme is deleted or missing. Restore parent programme first.";
                String parentInfo = parentProg.map(p -> "Programme: " + p.getName() + " (" + p.getDegreeAwarded() + ")")
                        .orElse("Programme ID: " + b.getMasterProgrammeId() + " (Deleted / In Trash)");

                Map<String, Object> details = new HashMap<>();
                details.put("masterProgrammeId", b.getMasterProgrammeId());
                details.put("startYear", b.getStartYear());
                details.put("endYear", b.getEndYear());
                details.put("coordinator", b.getCoordinatorName());

                items.add(DeletedItemDto.builder()
                        .id(b.getId())
                        .resourceType("BATCH")
                        .name(b.getName())
                        .code(b.getStartYear() != null && b.getEndYear() != null ? b.getStartYear() + " - " + b.getEndYear() : b.getId())
                        .category("Programme Batch")
                        .status(b.getStatus())
                        .deletedAt(b.getDeletedAt())
                        .deletedBy(b.getDeletedBy() != null ? b.getDeletedBy() : "Administrator")
                        .parentInfo(parentInfo)
                        .canRestore(canRestore)
                        .warningMessage(warning)
                        .details(details)
                        .build());
            }
        }

        // 3. Courses
        if ("ALL".equals(filterType) || "COURSE".equals(filterType) || "PROGRAMME_BATCH_COURSE".equals(filterType)) {
            List<ProgrammeBatchCourse> trashCourses = programmeBatchCourseRepository.findAllTrashCourses();
            for (ProgrammeBatchCourse c : trashCourses) {
                Optional<ProgrammeBatch> parentBatch = programmeBatchRepository.findByIdAndDeletedAtIsNull(c.getProgrammeBatchId());
                boolean canRestore = parentBatch.isPresent();
                String warning = canRestore ? null : "Parent Batch is deleted or missing. Restore parent batch first.";
                String parentInfo = parentBatch.map(b -> "Batch: " + b.getName())
                        .orElse("Batch ID: " + c.getProgrammeBatchId() + " (Deleted / In Trash)");

                Map<String, Object> details = new HashMap<>();
                details.put("programmeBatchId", c.getProgrammeBatchId());
                details.put("semester", c.getSemester());
                details.put("credits", c.getCredits());
                details.put("courseType", c.getCourseType());
                details.put("coordinator", c.getCourseCoordinatorName());

                items.add(DeletedItemDto.builder()
                        .id(c.getId())
                        .resourceType("COURSE")
                        .name(c.getName() != null ? c.getName() : c.getCode())
                        .code(c.getCode())
                        .category("Course Offering")
                        .status(c.getStatus())
                        .deletedAt(c.getDeletedAt())
                        .deletedBy(c.getDeletedBy() != null ? c.getDeletedBy() : "Administrator")
                        .parentInfo(parentInfo)
                        .canRestore(canRestore)
                        .warningMessage(warning)
                        .details(details)
                        .build());
            }
        }

        // 4. Users (Deactivated)
        if ("ALL".equals(filterType) || "USER".equals(filterType)) {
            List<User> deactivatedUsers = userRepository.findDeactivatedUsers();
            for (User u : deactivatedUsers) {
                Map<String, Object> details = new HashMap<>();
                details.put("email", u.getEmail());
                details.put("role", u.getRole() != null ? u.getRole().name() : "");
                details.put("schoolId", u.getSchoolId());
                details.put("departmentId", u.getDepartmentId());

                items.add(DeletedItemDto.builder()
                        .id(String.valueOf(u.getId()))
                        .resourceType("USER")
                        .name(u.getName())
                        .code(u.getUsername())
                        .category("User Account")
                        .status("INACTIVE")
                        .deletedAt(u.getUpdatedAt() != null ? u.getUpdatedAt() : u.getCreatedAt())
                        .deletedBy("Administrator")
                        .parentInfo("Role: " + (u.getRole() != null ? u.getRole().name() : "N/A") + (u.getDepartmentId() != null ? " • Dept: " + u.getDepartmentId() : ""))
                        .canRestore(true)
                        .warningMessage(null)
                        .details(details)
                        .build());
            }
        }

        // Apply search filter
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            items = items.stream().filter(item ->
                    (item.getName() != null && item.getName().toLowerCase().contains(q)) ||
                    (item.getCode() != null && item.getCode().toLowerCase().contains(q)) ||
                    (item.getId() != null && item.getId().toLowerCase().contains(q)) ||
                    (item.getDeletedBy() != null && item.getDeletedBy().toLowerCase().contains(q)) ||
                    (item.getParentInfo() != null && item.getParentInfo().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        // Apply date range filters
        if (from != null) {
            items = items.stream().filter(item -> item.getDeletedAt() == null || !item.getDeletedAt().isBefore(from)).collect(Collectors.toList());
        }
        if (to != null) {
            items = items.stream().filter(item -> item.getDeletedAt() == null || !item.getDeletedAt().isAfter(to)).collect(Collectors.toList());
        }

        // Sort descending by deletedAt
        items.sort((a, b) -> {
            if (a.getDeletedAt() == null && b.getDeletedAt() == null) return 0;
            if (a.getDeletedAt() == null) return 1;
            if (b.getDeletedAt() == null) return -1;
            return b.getDeletedAt().compareTo(a.getDeletedAt());
        });

        return items;
    }

    @Transactional(readOnly = true)
    public RecoverySummaryDto getSummary() {
        long progCount = masterProgrammeRepository.findAllTrashProgrammes().size();
        long batchCount = programmeBatchRepository.findAllTrashBatches().size();
        long courseCount = programmeBatchCourseRepository.findAllTrashCourses().size();
        long userCount = userRepository.findDeactivatedUsers().size();

        return RecoverySummaryDto.builder()
                .programmesCount(progCount)
                .batchesCount(batchCount)
                .coursesCount(courseCount)
                .usersCount(userCount)
                .totalDeleted(progCount + batchCount + courseCount + userCount)
                .build();
    }

    @Transactional
    public DeletedItemDto restoreItem(String resourceType, String id, String reason) {
        if (resourceType == null || id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource type and item ID must not be null.");
        }

        String type = resourceType.trim().toUpperCase();
        String auditReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Restored from trash by IQAC";

        switch (type) {
            case "PROGRAMME":
            case "MASTER_PROGRAMME": {
                MasterProgramme prog = masterProgrammeRepository.findTrashProgrammeById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted programme not found with ID: " + id));

                masterProgrammeRepository.restoreTrashProgrammeById(id);
                academicLookupCacheService.evictMasterProgrammeCache();

                auditLogService.record(
                        AuditAction.RESTORE,
                        ResourceType.MASTER_PROGRAMME,
                        id,
                        "DELETED",
                        "ACTIVE",
                        auditReason,
                        Map.of("id", id, "name", prog.getName(), "code", prog.getDegreeAwarded()),
                        true
                );

                return DeletedItemDto.builder()
                        .id(prog.getId())
                        .resourceType("PROGRAMME")
                        .name(prog.getName())
                        .code(prog.getDegreeAwarded())
                        .status("ACTIVE")
                        .build();
            }

            case "BATCH":
            case "PROGRAMME_BATCH": {
                ProgrammeBatch batch = programmeBatchRepository.findTrashBatchById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted batch not found with ID: " + id));

                // Hierarchy validation: Parent programme must be active
                if (!masterProgrammeRepository.findByIdAndDeletedAtIsNull(batch.getMasterProgrammeId()).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot restore batch: Parent Programme is deleted or inactive. Please restore the parent Programme first.");
                }

                programmeBatchRepository.restoreTrashBatchById(id);
                academicLookupCacheService.evictProgrammeBatchCache();

                auditLogService.record(
                        AuditAction.RESTORE,
                        ResourceType.PROGRAMME_BATCH,
                        id,
                        "DELETED",
                        "ACTIVE",
                        auditReason,
                        Map.of("id", id, "name", batch.getName(), "masterProgrammeId", batch.getMasterProgrammeId()),
                        true
                );

                return DeletedItemDto.builder()
                        .id(batch.getId())
                        .resourceType("BATCH")
                        .name(batch.getName())
                        .code(batch.getStartYear() + " - " + batch.getEndYear())
                        .status("ACTIVE")
                        .build();
            }

            case "COURSE":
            case "PROGRAMME_BATCH_COURSE": {
                ProgrammeBatchCourse course = programmeBatchCourseRepository.findTrashCourseById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deleted course offering not found with ID: " + id));

                // Hierarchy validation: Parent batch must be active
                if (!programmeBatchRepository.findByIdAndDeletedAtIsNull(course.getProgrammeBatchId()).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot restore course offering: Parent Batch is deleted or inactive. Please restore the parent Batch first.");
                }

                programmeBatchCourseRepository.restoreTrashCourseById(id);
                academicLookupCacheService.evictCourseCache();

                auditLogService.record(
                        AuditAction.RESTORE,
                        ResourceType.PROGRAMME_BATCH_COURSE,
                        id,
                        "DELETED",
                        "ACTIVE",
                        auditReason,
                        Map.of("id", id, "name", course.getName() != null ? course.getName() : "", "code", course.getCode() != null ? course.getCode() : "", "programmeBatchId", course.getProgrammeBatchId()),
                        true
                );

                return DeletedItemDto.builder()
                        .id(course.getId())
                        .resourceType("COURSE")
                        .name(course.getName())
                        .code(course.getCode())
                        .status("ACTIVE")
                        .build();
            }

            case "USER": {
                Long userId;
                try {
                    userId = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user ID: " + id);
                }

                User user = userRepository.findDeactivatedUserById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deactivated user not found with ID: " + id));

                userRepository.reactivateUserById(userId);

                auditLogService.record(
                        AuditAction.RESTORE,
                        ResourceType.USER,
                        id,
                        "INACTIVE",
                        "ACTIVE",
                        auditReason,
                        Map.of("id", userId, "username", user.getUsername(), "email", user.getEmail(), "role", user.getRole() != null ? user.getRole().name() : ""),
                        true
                );

                return DeletedItemDto.builder()
                        .id(String.valueOf(user.getId()))
                        .resourceType("USER")
                        .name(user.getName())
                        .code(user.getUsername())
                        .status("ACTIVE")
                        .build();
            }

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resource type for recovery: " + resourceType);
        }
    }
}
