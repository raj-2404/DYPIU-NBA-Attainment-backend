package com.dypiu.nba.service;

import com.dypiu.nba.entity.Department;
import com.dypiu.nba.entity.MasterProgramme;
import com.dypiu.nba.entity.ProgrammeBatch;
import com.dypiu.nba.repository.DepartmentRepository;
import com.dypiu.nba.repository.MasterProgrammeRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class BatchLifecycleService {

    private final ProgrammeBatchRepository programmeBatchRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final DepartmentRepository departmentRepository;
    private final CurrentUserScopeService currentUserScopeService;
    private final AuditLogService auditLogService;
    private final com.dypiu.nba.repository.ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    private void enforceBatchScope(ProgrammeBatch batch, CurrentUserScope scope) {
        if (scope == null || scope.isIqac()) return;
        if (batch == null || batch.getMasterProgrammeId() == null) return;

        MasterProgramme prog = masterProgrammeRepository.findById(batch.getMasterProgrammeId()).orElse(null);
        if (prog == null) return;

        if (scope.isDirector()) {
            if (prog.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
                if (dept != null && dept.getSchoolId() != null && !dept.getSchoolId().equals(scope.getRequiredSchoolId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Batch belongs to a department outside your assigned school scope.");
                }
            }
        } else if (scope.isHod()) {
            if (prog.getDepartmentId() != null && !prog.getDepartmentId().equals(scope.getRequiredDepartmentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Batch belongs to a programme outside your assigned department scope.");
            }
        }
    }

    @Transactional(readOnly = true)
    public void enforceBatchEditability(String programmeBatchId) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) return;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + programmeBatchId));

        if ("INACTIVE".equalsIgnoreCase(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify data for an INACTIVE batch.");
        }

        if ("COMPLETED".equalsIgnoreCase(batch.getStatus())) {
            if (batch.getEditingWindowUntil() == null || batch.getEditingWindowUntil().isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify batch data: Programme batch '" + batch.getName() + "' is COMPLETED. All course allocations, outcomes, mappings, and attainment data are locked unless a reopening window is currently active. Only Programme ATR editing is currently permitted.");
            }
        }

        if ("GRADUATED".equalsIgnoreCase(batch.getStatus())) {
            if (batch.getEditingWindowUntil() == null || batch.getEditingWindowUntil().isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify batch data: Programme batch '" + batch.getName() + "' is GRADUATED. All records across this batch are permanently locked unless a reopening window is currently active.");
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isBatchEditable(String programmeBatchId) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) return true;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElse(null);
        if (batch == null) return true;

        if ("INACTIVE".equalsIgnoreCase(batch.getStatus())) return false;
        if ("GRADUATED".equalsIgnoreCase(batch.getStatus()) || "COMPLETED".equalsIgnoreCase(batch.getStatus())) {
            return batch.getEditingWindowUntil() != null && !batch.getEditingWindowUntil().isBefore(ZonedDateTime.now());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isSemesterCompleted(String programmeBatchId, Integer semester) {
        if (programmeBatchId == null || semester == null) return false;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElse(null);
        if (batch == null) return false;
        java.util.List<com.dypiu.nba.entity.ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId())
                .stream()
                .filter(o -> java.util.Objects.equals(o.getSemester(), semester))
                .toList();
        if (offerings.isEmpty()) return false;
        return offerings.stream().allMatch(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()));
    }

    @Transactional(readOnly = true)
    public void enforceSemesterEditability(String programmeBatchId, Integer semester) {
        enforceBatchEditability(programmeBatchId);
        if (semester != null && isSemesterCompleted(programmeBatchId, semester)) {
            ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                    .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                    .orElse(null);
            String batchName = batch != null ? batch.getName() : programmeBatchId;
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify data: Semester " + semester + " of Programme batch '" + batchName + "' is COMPLETED and locked.");
        }
    }

    @Transactional(readOnly = true)
    public boolean isBatchConcluded(ProgrammeBatch batch) {
        if (batch == null) return false;
        String status = batch.getStatus() != null ? batch.getStatus().trim().toUpperCase() : "";
        return "COMPLETED".equals(status) || "GRADUATED".equals(status);
    }

    @Transactional(readOnly = true)
    public boolean isBatchConcluded(String programmeBatchId) {
        if (programmeBatchId == null || programmeBatchId.isBlank()) return false;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElse(null);
        return isBatchConcluded(batch);
    }

    @Transactional
    public ProgrammeBatch reopenGraduatedBatch(String programmeBatchId, ZonedDateTime editingUntil, String reason) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + programmeBatchId));
        
        if (!"GRADUATED".equalsIgnoreCase(batch.getStatus()) && !"COMPLETED".equalsIgnoreCase(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMPLETED or GRADUATED batches can be reopened.");
        }
        
        CurrentUserScope scope;
        try {
            scope = currentUserScopeService.getCurrentUserScope();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated.");
        }
        
        if (scope == null || (!scope.isHod() && !scope.isDirector() && !scope.isIqac())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only authorized HOD or higher authority can reopen a graduated batch.");
        }
        enforceBatchScope(batch, scope);

        batch.setEditingWindowOpenedAt(ZonedDateTime.now());
        batch.setEditingWindowUntil(editingUntil);
        batch.setEditingWindowOpenedBy(scope.getEmail() != null ? scope.getEmail() : scope.getName());
        
        ProgrammeBatch saved = programmeBatchRepository.save(batch);

        auditLogService.recordSuccess(
                com.dypiu.nba.audit.AuditAction.UPDATE,
                com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH,
                programmeBatchId,
                "GRADUATED",
                "REOPENED_UNTIL_" + editingUntil.toString(),
                reason != null ? reason : "Historical data entry window opened",
                null
        );

        return saved;
    }

    @Transactional
    public ProgrammeBatch closeReopeningWindow(String programmeBatchId, String reason) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + programmeBatchId));

        CurrentUserScope scope;
        try {
            scope = currentUserScopeService.getCurrentUserScope();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated.");
        }

        if (scope == null || (!scope.isHod() && !scope.isDirector() && !scope.isIqac())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only authorized HOD or higher authority can close a reopening window.");
        }
        enforceBatchScope(batch, scope);

        String prevState = batch.getEditingWindowUntil() != null ? "REOPENED_UNTIL_" + batch.getEditingWindowUntil().toString() : "GRADUATED";

        batch.setEditingWindowUntil(null);
        batch.setEditingWindowOpenedAt(null);
        batch.setEditingWindowOpenedBy(null);

        ProgrammeBatch saved = programmeBatchRepository.save(batch);

        auditLogService.recordSuccess(
                com.dypiu.nba.audit.AuditAction.UPDATE,
                com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH,
                programmeBatchId,
                prevState,
                "GRADUATED",
                reason != null ? reason : "Historical data entry window closed manually",
                null
        );

        return saved;
    }

    @Transactional
    public ProgrammeBatch changeBatchStatus(String programmeBatchId, String newStatus, String reason) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + programmeBatchId));

        if (!"ACTIVE".equalsIgnoreCase(newStatus) && !"INACTIVE".equalsIgnoreCase(newStatus) && !"GRADUATED".equalsIgnoreCase(newStatus) && !"COMPLETED".equalsIgnoreCase(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid batch status. Allowed values: ACTIVE, INACTIVE, COMPLETED, GRADUATED.");
        }

        CurrentUserScope scope;
        try {
            scope = currentUserScopeService.getCurrentUserScope();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated.");
        }

        if (scope == null || (!scope.isHod() && !scope.isDirector() && !scope.isIqac())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only authorized HOD or higher authority can change batch status.");
        }
        enforceBatchScope(batch, scope);

        String oldStatus = batch.getStatus();
        
        // If changing to GRADUATED or COMPLETED, clear any active editing window just in case
        if ("GRADUATED".equalsIgnoreCase(newStatus) || "COMPLETED".equalsIgnoreCase(newStatus)) {
            batch.setEditingWindowUntil(null);
            batch.setEditingWindowOpenedAt(null);
            batch.setEditingWindowOpenedBy(null);
        }

        batch.setStatus(newStatus.toUpperCase());
        ProgrammeBatch saved = programmeBatchRepository.save(batch);

        auditLogService.recordSuccess(
                com.dypiu.nba.audit.AuditAction.UPDATE,
                com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH,
                programmeBatchId,
                oldStatus,
                newStatus.toUpperCase(),
                reason != null ? reason : "Batch status changed",
                null
        );

        return saved;
    }
}
