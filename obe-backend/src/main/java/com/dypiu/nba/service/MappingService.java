package com.dypiu.nba.service;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MappingService {

    private final CoPoMappingRepository coPoMappingRepository;
    private final CoPsoMappingRepository coPsoMappingRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final DepartmentRepository departmentRepository;
    private final CurrentUserScopeService currentUserScopeService;
    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final BatchLifecycleService batchLifecycleService;

    private CurrentUserScope getScope() {
        try {
            return currentUserScopeService != null ? currentUserScopeService.getCurrentUserScope() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final java.util.Comparator<ApprovalRequest> LATEST_APPROVAL_COMPARATOR = (a, b) -> {
        ZonedDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : (a.getApprovedAt() != null ? a.getApprovedAt() : (a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt()));
        ZonedDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : (b.getApprovedAt() != null ? b.getApprovedAt() : (b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt()));
        if (ta != null && tb != null) {
            int cmp = ta.compareTo(tb);
            if (cmp != 0) return cmp;
        }
        if (ta == null && tb != null) return -1;
        if (ta != null && tb == null) return 1;
        if (a.getId() != null && b.getId() != null) {
            return a.getId().compareTo(b.getId());
        }
        return 0;
    };

    private boolean isCourseAllocationApproved(ProgrammeBatchCourse offering) {
        if (offering == null) return false;
        if (offering.getProgrammeBatchId() != null && offering.getSemester() != null) {
            String semKey = "allocation-" + offering.getProgrammeBatchId().trim() + "-sem-" + offering.getSemester();
            boolean semApproved = approvalRequestRepository.findAll().stream()
                    .filter(a -> a.getType() == ApprovalType.COURSE_ALLOCATION && (semKey.equalsIgnoreCase(a.getResourceId()) || semKey.equalsIgnoreCase(a.getProgrammeBatchId())))
                    .max(LATEST_APPROVAL_COMPARATOR)
                    .map(a -> a.getStatus() == ApprovalStatus.APPROVED)
                    .orElse(false);
            if (semApproved) return true;
        }
        String progId = null;
        if (offering.getProgrammeBatchId() != null) {
            ProgrammeBatch b = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
            if (b != null && b.getMasterProgrammeId() != null) {
                progId = b.getMasterProgrammeId();
            }
        }
        if (progId == null || progId.isBlank()) return false;
        final String targetProgId = progId.replace("allocation-", "").replace("allocation_", "").replace("allocation", "").trim();
        return approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.COURSE_ALLOCATION || a.getType() == ApprovalType.COURSE_OFFERING)
                        && (targetProgId.equalsIgnoreCase(a.getMasterProgrammeId())
                        || ("allocation-" + targetProgId).equalsIgnoreCase(a.getResourceId())
                        || targetProgId.equalsIgnoreCase(a.getResourceId())
                        || (a.getResourceId() != null && a.getResourceId().toLowerCase().contains(targetProgId.toLowerCase()))))
                .max(LATEST_APPROVAL_COMPARATOR)
                .map(a -> a.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
    }

    private void enforceOutcomeScope(String courseOutcomeId) {
        if (courseOutcomeId == null || courseOutcomeId.isBlank()) return;
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;

        CourseOutcome co = courseOutcomeRepository.findById(courseOutcomeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Outcome not found: " + courseOutcomeId));

        String offeringId = co.getProgrammeBatchCourseId();
        if (offeringId != null && !offeringId.isBlank()) {
            ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + offeringId));

            if (scope.isFaculty()) {
                boolean isCoord = (offering.getCourseCoordinatorId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()));
                boolean isAssigned = isCoord || (offering.getAssignedFaculty() != null && (offering.getAssignedFaculty().contains(scope.getEmail()) || offering.getAssignedFaculty().contains(scope.getName())));
                if (!isAssigned) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this Course Offering.");
                }
                if (!isCourseAllocationApproved(offering)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
                }
                return;
            }

            if (offering.getProgrammeBatchId() != null) {
                ProgrammeBatch b = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
                if (b != null && b.getMasterProgrammeId() != null) {
                    String progId = b.getMasterProgrammeId();
                    if (scope.isProgrammeCoordinator() && !progId.equals(scope.getRequiredMasterProgrammeId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course Outcome is outside your assigned programme scope.");
                    }
                    MasterProgramme p = masterProgrammeRepository.findById(progId).orElse(null);
                    if (p != null && p.getDepartmentId() != null) {
                        if (scope.isHod() && !p.getDepartmentId().equals(scope.getRequiredDepartmentId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course Outcome is outside your assigned department scope.");
                        }
                        Department d = departmentRepository.findById(p.getDepartmentId()).orElse(null);
                        if (d != null && d.getSchoolId() != null && scope.isDirector() && !d.getSchoolId().equals(scope.getRequiredSchoolId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course Outcome is outside your assigned school scope.");
                        }
                    }
                }
            }
            return;
        }
    }

    private void enforceOutcomeEditability(String courseOutcomeId) {
        if (courseOutcomeId == null || courseOutcomeId.isBlank()) return;
        CourseOutcome co = courseOutcomeRepository.findById(courseOutcomeId).orElse(null);
        if (co != null && co.getProgrammeBatchCourseId() != null) {
            ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(co.getProgrammeBatchCourseId()).orElse(null);
            if (offering != null && offering.getProgrammeBatchId() != null) {
                batchLifecycleService.enforceBatchEditability(offering.getProgrammeBatchId());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CoPoMapping> getCoPoMappings(String courseOutcomeId) {
        System.out.println("[MappingService] getCoPoMappings called | courseOutcomeId: " + courseOutcomeId);
        enforceOutcomeScope(courseOutcomeId);
        return coPoMappingRepository.findByCourseOutcomeId(courseOutcomeId);
    }

    @Transactional
    public List<CoPoMapping> saveCoPoMappings(String courseOutcomeId, List<CoPoMapping> mappings) {
        System.out.println("[MappingService] saveCoPoMappings called | courseOutcomeId: " + courseOutcomeId + " | count: " + (mappings != null ? mappings.size() : 0));
        enforceOutcomeScope(courseOutcomeId);
        enforceOutcomeEditability(courseOutcomeId);
        coPoMappingRepository.deleteByCourseOutcomeId(courseOutcomeId);
        mappings.forEach(m -> {
            m.setCourseOutcomeId(courseOutcomeId);
            if (m.getId() == null) m.setId("copo-" + UUID.randomUUID().toString().substring(0, 8));
        });
        return coPoMappingRepository.saveAll(mappings);
    }

    @Transactional(readOnly = true)
    public List<CoPsoMapping> getCoPsoMappings(String courseOutcomeId) {
        System.out.println("[MappingService] getCoPsoMappings called | courseOutcomeId: " + courseOutcomeId);
        enforceOutcomeScope(courseOutcomeId);
        return coPsoMappingRepository.findByCourseOutcomeId(courseOutcomeId);
    }

    @Transactional
    public List<CoPsoMapping> saveCoPsoMappings(String courseOutcomeId, List<CoPsoMapping> mappings) {
        System.out.println("[MappingService] saveCoPsoMappings called | courseOutcomeId: " + courseOutcomeId + " | count: " + (mappings != null ? mappings.size() : 0));
        enforceOutcomeScope(courseOutcomeId);
        enforceOutcomeEditability(courseOutcomeId);
        coPsoMappingRepository.deleteByCourseOutcomeId(courseOutcomeId);
        mappings.forEach(m -> {
            m.setCourseOutcomeId(courseOutcomeId);
            if (m.getId() == null) m.setId("copso-" + UUID.randomUUID().toString().substring(0, 8));
        });
        return coPsoMappingRepository.saveAll(mappings);
    }
}
