package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.exception.ResourceNotFoundException;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtrService {

    private final CourseAtrRepository courseAtrRepository;
    private final ProgrammeAtrRepository programmeAtrRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final ProgrammeOutcomeRepository programmeOutcomeRepository;
    private final ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;
    private final AttainmentCalculationService attainmentCalculationService;
    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;
    private final CurrentUserScopeService currentUserScopeService;
    private final AuditLogService auditLogService;
    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;
    private final BatchLifecycleService batchLifecycleService;

    private CurrentUserScope getScope() {
        try {
            return currentUserScopeService.getCurrentUserScope();
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceSchoolScope(String schoolId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (scope.isDirector() || scope.isHod() || scope.isProgrammeCoordinator()) {
            String requiredSchoolId = scope.getRequiredSchoolId();
            if (schoolId != null && !schoolId.equals(requiredSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned school scope.");
            }
        }
    }

    private void enforceDepartmentScope(String departmentId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (scope.isHod()) {
            String requiredDeptId = scope.getRequiredDepartmentId();
            if (departmentId != null && !departmentId.equals(requiredDeptId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned department scope.");
            }
        }
        if (scope.isProgrammeCoordinator()) {
            if (scope.hasDepartmentScope()) {
                String requiredDeptId = scope.getDepartmentId();
                if (departmentId != null && !departmentId.equals(requiredDeptId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned department scope.");
                }
            }
            return;
        }
        if (scope.isDirector()) {
            if (departmentId != null) {
                Department dept = departmentRepository.findById(departmentId).orElse(null);
                if (dept != null && dept.getSchoolId() != null && !dept.getSchoolId().equals(scope.getRequiredSchoolId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Department is outside your assigned school scope.");
                }
            }
        }
    }

    private void enforceProgrammeScope(String masterProgrammeId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (masterProgrammeId == null || masterProgrammeId.isBlank()) return;

        if (scope.isProgrammeCoordinator()) {
            String requiredProgId = scope.getMasterProgrammeId();
            boolean matchesDirectProg = (requiredProgId != null && masterProgrammeId.equals(requiredProgId));
            boolean matchesBatchProg = false;
            if (!matchesDirectProg && scope.getEmail() != null && !scope.getEmail().isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(scope.getEmail().trim());
                matchesBatchProg = batches.stream().anyMatch(b -> masterProgrammeId.equals(b.getMasterProgrammeId()));
            }
            if (!matchesDirectProg && !matchesBatchProg) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned programme scope.");
            }
        }

        MasterProgramme prog = masterProgrammeRepository.findById(masterProgrammeId)
                .orElseThrow(() -> new ResourceNotFoundException("Programme not found: " + masterProgrammeId));
        if (prog.getDepartmentId() != null) {
            enforceDepartmentScope(prog.getDepartmentId());
            Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
            if (dept != null && dept.getSchoolId() != null) {
                enforceSchoolScope(dept.getSchoolId());
            }
        }
    }

    private void enforceBatchScope(String programmeBatchId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (programmeBatchId == null || programmeBatchId.isBlank()) return;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + programmeBatchId));

        if (scope.isFaculty()) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
            boolean hasAssigned = offerings.stream().anyMatch(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                return isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
            });
            if (!hasAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to any Course Offering in this Batch.");
            }
            return;
        }

        if (scope.isProgrammeCoordinator()) {
            boolean isAssigned = (scope.getUserId() != null && Objects.equals(batch.getCoordinatorId(), scope.getUserId()))
                    || (scope.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim()))
                    || (scope.getName() != null && batch.getCoordinatorName() != null && batch.getCoordinatorName().trim().equalsIgnoreCase(scope.getName().trim()))
                    || (scope.getMasterProgrammeId() != null && scope.getMasterProgrammeId().equalsIgnoreCase(batch.getMasterProgrammeId()));
            if (!isAssigned && batch.getMasterProgrammeId() != null) {
                MasterProgramme prog = masterProgrammeRepository.findById(batch.getMasterProgrammeId()).orElse(null);
                if (prog != null) {
                    boolean progCoord = (scope.getEmail() != null && prog.getCoordinatorEmail() != null && prog.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim()))
                            || (scope.getName() != null && prog.getCoordinator() != null && prog.getCoordinator().trim().equalsIgnoreCase(scope.getName().trim()));
                    if (progCoord) {
                        isAssigned = true;
                    }
                }
            }
            if (!isAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not the assigned Programme Coordinator for this Programme Batch.");
            }
            return;
        }

        enforceProgrammeScope(batch.getMasterProgrammeId());
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

    private void enforceCourseScope(String courseOfferingOrId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (courseOfferingOrId == null || courseOfferingOrId.isBlank()) return;
        enforceOfferingScope(courseOfferingOrId);
    }

    private void enforceOfferingScope(String offeringId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (offeringId == null || offeringId.isBlank()) return;
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));

        if (scope.isFaculty()) {
            boolean isCoordinator = (offering.getCourseCoordinatorId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()));
            boolean isAssigned = isCoordinator || (offering.getAssignedFaculty() != null && (offering.getAssignedFaculty().contains(scope.getEmail()) || offering.getAssignedFaculty().contains(scope.getName())));
            if (!isAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this Course Offering.");
            }
            if (!isCourseAllocationApproved(offering)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
            }
            return;
        }

        if (offering.getProgrammeBatchId() != null) {
            enforceBatchScope(offering.getProgrammeBatchId());
            return;
        }
        if (offering.getMasterCourseId() != null) enforceCourseScope(offering.getMasterCourseId());
    }

    private void enforceCourseCoordinatorScope(String offeringId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac() || scope.isDirector() || scope.isHod() || scope.isProgrammeCoordinator()) {
            return;
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
        String coordEmail = offering.getCourseCoordinatorEmail() != null ? offering.getCourseCoordinatorEmail() : offering.getCoordinatorEmail();
        boolean isCoordinator = (offering.getCourseCoordinatorId() != null && scope.getUserId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()))
                || (coordEmail != null && scope.getEmail() != null && !coordEmail.isBlank() && !scope.getEmail().isBlank() && coordEmail.trim().equalsIgnoreCase(scope.getEmail().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getName() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getName().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getName().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getUsername() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getUsername().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getUsername().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getEmail() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getEmail().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getEmail().trim()));
        if (!isCoordinator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only the assigned Course Coordinator can perform this action.");
        }
        if (!isCourseAllocationApproved(offering)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
        }
    }

    private void enforceCourseOrOfferingScope(String courseOfferingOrMasterCourseId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (courseOfferingOrMasterCourseId == null || courseOfferingOrMasterCourseId.isBlank()) return;
        if (programmeBatchCourseRepository.existsById(courseOfferingOrMasterCourseId)) {
            enforceOfferingScope(courseOfferingOrMasterCourseId);
        }
    }

    private void enforceOfferingEditability(String offeringId) {
        if (offeringId == null || offeringId.isBlank()) return;
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
        if (offering != null && offering.getProgrammeBatchId() != null) {
            batchLifecycleService.enforceBatchEditability(offering.getProgrammeBatchId());
        }
    }

    // --- Course ATR Support ---

    @Transactional(readOnly = true)
    public List<CourseAtr> getCourseAtrs(String courseOfferingOrMasterCourseId) {
        System.out.println("[AtrService] getCourseAtrs called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        if (courseOfferingOrMasterCourseId != null && !courseOfferingOrMasterCourseId.isBlank()) {
            enforceCourseOrOfferingScope(courseOfferingOrMasterCourseId);
        }
        List<CourseAtr> list = courseAtrRepository.findByProgrammeBatchCourseId(courseOfferingOrMasterCourseId);
        if (!list.isEmpty()) return list;

        // If masterCourseId was passed, find corresponding course offerings
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(courseOfferingOrMasterCourseId);
        if (!offerings.isEmpty()) {
            return courseAtrRepository.findByProgrammeBatchCourseId(offerings.get(0).getId());
        }
        return Collections.emptyList();
    }

    @Transactional
    public List<CourseAtr> saveCourseAtrs(String programmeBatchCourseId, List<CourseAtr> atrs) {
        System.out.println("[AtrService] saveCourseAtrs called | programmeBatchCourseId: " + programmeBatchCourseId + " | count: " + (atrs != null ? atrs.size() : 0));
        String targetOfferingId = resolveOfferingId(programmeBatchCourseId);
        if (targetOfferingId != null && !targetOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(targetOfferingId);
            enforceOfferingEditability(targetOfferingId);
            if (approvalService != null) {
                approvalService.resetToDraftOnModification(ApprovalType.COURSE_ATR, targetOfferingId, null);
            }
        }
        List<CourseAtr> existing = courseAtrRepository.findByProgrammeBatchCourseId(targetOfferingId);
        Map<String, CourseAtr> existingByCode = existing.stream()
                .filter(e -> e.getCoCode() != null)
                .collect(Collectors.toMap(e -> e.getCoCode().toUpperCase(), e -> e, (a, b) -> a));

        List<CourseAtr> toSave = new ArrayList<>();
        if (atrs != null) {
            for (CourseAtr a : atrs) {
                String codeKey = a.getCoCode() != null ? a.getCoCode().toUpperCase() : "";
                CourseAtr target = existingByCode.get(codeKey);
                if (target == null) {
                    target = a;
                    target.setProgrammeBatchCourseId(targetOfferingId);
                    if (target.getId() == null || target.getId().isBlank()) {
                        target.setId("atr-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                } else {
                    target.setStatement(a.getStatement());
                    target.setTitle(a.getTitle());
                    target.setTargetScore(a.getTargetScore());
                    target.setActualScore(a.getActualScore());
                    target.setPctAchieved(a.getPctAchieved() != null ? a.getPctAchieved() : BigDecimal.ZERO);
                    target.setActionsJson(a.getActionsJson());
                }
                if (target.getStatus() == null) {
                    target.setStatus(CourseAtrStatus.DRAFT);
                }
                target.setUpdatedAt(ZonedDateTime.now());
                toSave.add(target);
            }
        }
        return courseAtrRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public Optional<ProgrammeAtr> getProgrammeAtr(String programmeBatchId) {
        System.out.println("[AtrService] getProgrammeAtr called | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            enforceBatchScope(programmeBatchId);
        }
        return programmeAtrRepository.findByProgrammeBatchId(programmeBatchId);
    }

    @Transactional(readOnly = true)
    public Optional<ProgrammeAtr> getProgrammeAtrByBatch(String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AtrService] getProgrammeAtrByBatch called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            enforceProgrammeScope(masterProgrammeId);
        }
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            enforceBatchScope(programmeBatchId);
            return programmeAtrRepository.findByProgrammeBatchId(programmeBatchId);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ProgrammeAtr> getPreviousBatchProgrammeAtr(String programmeBatchId) {
        System.out.println("[AtrService] getPreviousBatchProgrammeAtr called | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) return Optional.empty();
        enforceBatchScope(programmeBatchId);
        
        ProgrammeBatch currentBatch = programmeBatchRepository.findById(programmeBatchId).orElse(null);
        if (currentBatch == null || currentBatch.getStartYear() == null || currentBatch.getEndYear() == null) {
            return Optional.empty();
        }
        
        int prevStartYear = currentBatch.getStartYear() - 1;
        int prevEndYear = currentBatch.getEndYear() - 1;
        
        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(currentBatch.getMasterProgrammeId());
        ProgrammeBatch previousBatch = batches.stream()
            .filter(b -> b.getStartYear() != null && b.getEndYear() != null 
                    && b.getStartYear() == prevStartYear 
                    && b.getEndYear() == prevEndYear)
            .findFirst()
            .orElse(null);
            
        if (previousBatch == null) return Optional.empty();
        
        return programmeAtrRepository.findByProgrammeBatchId(previousBatch.getId());
    }

    @Transactional(readOnly = true)
    public ProgrammeAtrReportDto getPreviousYearProgrammeAtrReport(String programmeBatchId) {
        System.out.println("[AtrService] getPreviousYearProgrammeAtrReport called | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) return null;
        enforceBatchScope(programmeBatchId);
        ProgrammeBatch currentBatch = programmeBatchRepository.findById(programmeBatchId).orElse(null);
        if (currentBatch == null || currentBatch.getStartYear() == null || currentBatch.getEndYear() == null) return null;
        
        int targetStartYear = currentBatch.getStartYear() - 1;
        int targetEndYear = currentBatch.getEndYear() - 1;
        
        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeIdOrderByStartYearDesc(currentBatch.getMasterProgrammeId());
        ProgrammeBatch targetBatch = batches.stream()
            .filter(b -> b.getStartYear() != null && b.getEndYear() != null 
                    && b.getStartYear() == targetStartYear 
                    && b.getEndYear() == targetEndYear)
            .findFirst()
            .orElse(null);
            
        if (targetBatch == null) return null;
        return getProgrammeAtrReport(currentBatch.getMasterProgrammeId(), targetBatch.getId());
    }

    @Transactional
    public ProgrammeAtr saveProgrammeAtr(ProgrammeAtr atr) {
        System.out.println("[AtrService] saveProgrammeAtr called | id: " + (atr != null ? atr.getId() : "null") + " | programmeBatchId: " + (atr != null ? atr.getProgrammeBatchId() : "null"));
        if (atr != null && atr.getProgrammeBatchId() != null) {
            enforceBatchScope(atr.getProgrammeBatchId());
            batchLifecycleService.enforceBatchEditability(atr.getProgrammeBatchId());
            if (approvalService != null && approvalService.isProgrammeAtrApproved(atr.getProgrammeBatchId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Programme ATR. A revision must be requested first.");
            }
            ProgrammeAtr existing = programmeAtrRepository.findByProgrammeBatchId(atr.getProgrammeBatchId()).orElse(null);
            if (existing != null) {
                atr.setId(existing.getId());
                if (atr.getStatus() == null) atr.setStatus(existing.getStatus());
                if (atr.getSubmittedBy() == null) atr.setSubmittedBy(existing.getSubmittedBy());
                if (atr.getSubmittedAt() == null) atr.setSubmittedAt(existing.getSubmittedAt());
                if (atr.getVerifiedBy() == null) atr.setVerifiedBy(existing.getVerifiedBy());
                if (atr.getVerifiedAt() == null) atr.setVerifiedAt(existing.getVerifiedAt());
                if (atr.getVerificationComments() == null) atr.setVerificationComments(existing.getVerificationComments());
                if (atr.getObservationsJson() == null || atr.getObservationsJson().isBlank()) {
                    atr.setObservationsJson(existing.getObservationsJson());
                }
            }
        }
        if (atr.getId() == null || atr.getId().isBlank()) {
            atr.setId("patr-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (atr.getStatus() == null) {
            atr.setStatus(ProgrammeAtrStatus.DRAFT);
        }
        atr.setUpdatedAt(ZonedDateTime.now());
        return programmeAtrRepository.save(atr);
    }

    public String resolveOfferingId(String offeringOrMasterCourseId) {
        if (offeringOrMasterCourseId == null || offeringOrMasterCourseId.isBlank()) return null;
        if (programmeBatchCourseRepository.existsById(offeringOrMasterCourseId)) {
            return offeringOrMasterCourseId;
        }
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(offeringOrMasterCourseId);
        if (!offerings.isEmpty()) {
            return offerings.get(0).getId();
        }
        return offeringOrMasterCourseId;
    }

    // =========================================================================
    //  STANDARD COURSE ATR REPORT (OFFICIAL STRUCTURE)
    // =========================================================================

    @Transactional(readOnly = true)
    public CourseAtrReportDto getCourseAtrReport(String programmeBatchCourseId) {
        System.out.println("[AtrService] getCourseAtrReport called | programmeBatchCourseId: " + programmeBatchCourseId);
        String targetOfferingId = resolveOfferingId(programmeBatchCourseId);
        if (targetOfferingId != null && !targetOfferingId.isBlank()) {
            enforceOfferingScope(targetOfferingId);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(targetOfferingId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Offering not found: " + programmeBatchCourseId));

        return buildCourseAtrReport(offering);
    }

    private CourseAtrReportDto buildCourseAtrReport(ProgrammeBatchCourse offering) {
        ProgrammeBatch batch = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);

        List<CourseOutcome> cos = courseOutcomeRepository.findByProgrammeBatchCourseId(offering.getId());
        List<CourseAtr> existingAtrs = courseAtrRepository.findByProgrammeBatchCourseId(offering.getId());
        Map<String, CourseAtr> atrMap = existingAtrs.stream().collect(Collectors.toMap(CourseAtr::getCoCode, a -> a, (a, b) -> a));

        Map<String, BigDecimal> attainmentScoreMap = new HashMap<>();
        if (existingAtrs.isEmpty() && attainmentCalculationService != null) {
            try {
                Map<String, Object> calcResult = attainmentCalculationService.calculateCourseCoAttainment(offering.getId());
                if (calcResult != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) calcResult.getOrDefault("coAttainments", Collections.emptyList());
                    if (coAttainments != null) {
                        for (Map<String, Object> m : coAttainments) {
                            String code = (String) m.get("coCode");
                            BigDecimal combined = (BigDecimal) m.get("combinedAttainment");
                            if (code != null && combined != null) attainmentScoreMap.put(code, combined);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        List<CourseAtrReportDto.OutcomeRow> rows = new ArrayList<>();
        String atrStatus = existingAtrs.isEmpty() ? "DRAFT" : existingAtrs.get(0).getStatus().name();
        Set<String> processedCodes = new HashSet<>();

        for (CourseOutcome co : cos) {
            String coCode = co.getCode();
            processedCodes.add(coCode.toUpperCase());
            CourseAtr saved = atrMap.get(coCode);

            String statement = (saved != null && saved.getStatement() != null && !saved.getStatement().isBlank())
                    ? saved.getStatement()
                    : (co.getStatement() != null ? co.getStatement() : "Course Outcome " + coCode);

            BigDecimal target = (saved != null && saved.getTargetScore() != null)
                    ? saved.getTargetScore()
                    : (co.getTargetLevel() != null ? co.getTargetLevel() : new BigDecimal("2.50"));

            BigDecimal actual = (saved != null && saved.getActualScore() != null)
                    ? saved.getActualScore()
                    : attainmentScoreMap.getOrDefault(coCode, BigDecimal.ZERO);

            BigDecimal pct = (saved != null && saved.getPctAchieved() != null && saved.getPctAchieved().compareTo(BigDecimal.ZERO) > 0)
                    ? saved.getPctAchieved()
                    : (target.compareTo(BigDecimal.ZERO) > 0
                        ? actual.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);

            String obs = String.format("%s%% Target %s", pct, actual.compareTo(target) >= 0 ? "Achieved" : "Not Achieved");

            List<String> actions = new ArrayList<>();
            if (saved != null && saved.getActionsJson() != null && !saved.getActionsJson().isBlank()) {
                try {
                    actions = objectMapper.readValue(saved.getActionsJson(), new TypeReference<List<String>>() {});
                } catch (Exception ignored) {}
            }

            rows.add(CourseAtrReportDto.OutcomeRow.builder()
                    .outcomeCode(coCode)
                    .outcomeStatement(statement)
                    .targetLevel(target)
                    .attainmentLevel(actual)
                    .achievementPercentage(pct)
                    .actions(actions)
                    .build());
        }

        // Process any existing ATR records that might not be in CourseOutcome list
        for (CourseAtr saved : existingAtrs) {
            if (saved.getCoCode() != null && !processedCodes.contains(saved.getCoCode().toUpperCase())) {
                String coCode = saved.getCoCode();
                String statement = saved.getStatement() != null ? saved.getStatement() : "Course Outcome " + coCode;
                BigDecimal target = saved.getTargetScore() != null ? saved.getTargetScore() : new BigDecimal("2.50");
                BigDecimal actual = saved.getActualScore() != null ? saved.getActualScore() : BigDecimal.ZERO;
                BigDecimal pct = saved.getPctAchieved() != null ? saved.getPctAchieved() : BigDecimal.ZERO;
                List<String> actions = new ArrayList<>();
                if (saved.getActionsJson() != null && !saved.getActionsJson().isBlank()) {
                    try {
                        actions = objectMapper.readValue(saved.getActionsJson(), new TypeReference<List<String>>() {});
                    } catch (Exception ignored) {}
                }
                rows.add(CourseAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(coCode)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(actual)
                        .achievementPercentage(pct)
                        .actions(actions)
                        .build());
            }
        }

        return CourseAtrReportDto.builder()
                .reportType("COURSE_ATR")
                .courseAtrId(!existingAtrs.isEmpty() ? existingAtrs.get(0).getId() : "catr-" + offering.getId())
                .courseOffering(CourseAtrReportDto.CourseOfferingSummary.builder()
                        .id(offering.getId())
                        .masterCourseId(offering.getMasterCourseId())
                        .programmeBatchId(offering.getProgrammeBatchId())
                        .semester(offering.getSemester())
                        .build())
                .course(CourseAtrReportDto.CourseSummary.builder()
                        .id(offering.getId())
                        .code(offering.getEffectiveCourseCode() != null ? offering.getEffectiveCourseCode() : "")
                        .name(offering.getEffectiveCourseName() != null ? offering.getEffectiveCourseName() : "")
                        .build())
                .batch(batch != null ? CourseAtrReportDto.BatchSummary.builder().id(batch.getId()).name(batch.getName()).build() : null)
                .outcomes(rows)
                .status(atrStatus)
                .build();
    }

    @Transactional
    public CourseAtrReportDto saveCourseAtrReport(CourseAtrReportDto dto) {
        System.out.println("[AtrService] saveCourseAtrReport called | programmeBatchCourseId: " + (dto != null && dto.getCourseOffering() != null ? dto.getCourseOffering().getId() : "null"));
        if (dto == null || dto.getCourseOffering() == null || dto.getCourseOffering().getId() == null) {
            throw new IllegalArgumentException("Invalid Course ATR payload: CourseOffering is required.");
        }
        String offeringId = resolveOfferingId(dto.getCourseOffering().getId());
        enforceOfferingScope(offeringId);
        enforceOfferingEditability(offeringId);
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.COURSE_ATR, offeringId, null);
        }
        List<CourseAtr> toSave = new ArrayList<>();

        if (dto.getOutcomes() != null) {
            for (CourseAtrReportDto.OutcomeRow r : dto.getOutcomes()) {
                String actionsJson = "[]";
                try {
                    actionsJson = objectMapper.writeValueAsString(r.getActions() != null ? r.getActions() : Collections.emptyList());
                } catch (Exception ignored) {}

                CourseAtr atr = courseAtrRepository.findByProgrammeBatchCourseIdAndCoCode(offeringId, r.getOutcomeCode())
                        .orElseGet(() -> CourseAtr.builder()
                                .id("catr-" + UUID.randomUUID().toString().substring(0, 8))
                                .programmeBatchCourseId(offeringId)
                                .coCode(r.getOutcomeCode())
                                .build());

                atr.setStatement(r.getOutcomeStatement());
                atr.setTargetScore(r.getTargetLevel() != null ? r.getTargetLevel() : new BigDecimal("2.50"));
                atr.setActualScore(r.getAttainmentLevel() != null ? r.getAttainmentLevel() : BigDecimal.ZERO);
                atr.setPctAchieved(r.getAchievementPercentage() != null ? r.getAchievementPercentage() : BigDecimal.ZERO);
                atr.setActionsJson(actionsJson);
                atr.setStatus(dto.getStatus() != null ? CourseAtrStatus.valueOf(dto.getStatus()) : CourseAtrStatus.DRAFT);
                atr.setUpdatedAt(ZonedDateTime.now());

                toSave.add(atr);
            }
        }
        courseAtrRepository.saveAll(toSave);
        return getCourseAtrReport(offeringId);
    }

    @Transactional
    public CourseAtr submitCourseAtr(String programmeBatchCourseId, String submittedBy) {
        System.out.println("[AtrService] submitCourseAtr called | programmeBatchCourseId: " + programmeBatchCourseId + " | submittedBy: " + submittedBy);
        String targetOfferingId = resolveOfferingId(programmeBatchCourseId);
        if (targetOfferingId != null && !targetOfferingId.isBlank()) {
            enforceOfferingScope(targetOfferingId);
            enforceOfferingEditability(targetOfferingId);
            enforceCourseCoordinatorScope(targetOfferingId);
        }
        List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(targetOfferingId);
        if (atrs.isEmpty()) {
            saveCourseAtrReport(getCourseAtrReport(targetOfferingId));
            atrs = courseAtrRepository.findByProgrammeBatchCourseId(targetOfferingId);
        }
        boolean alreadyPending = atrs.stream().anyMatch(a -> a.getStatus() == CourseAtrStatus.SUBMITTED_FOR_VERIFICATION);
        if (alreadyPending) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course ATR has already been submitted and is pending review.");
        }
        boolean alreadyApproved = atrs.stream().anyMatch(a -> a.getStatus() == CourseAtrStatus.APPROVED || a.getStatus() == CourseAtrStatus.VERIFIED);
        if (alreadyApproved) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course ATR has already been approved.");
        }
        for (CourseAtr a : atrs) {
            a.setStatus(CourseAtrStatus.SUBMITTED_FOR_VERIFICATION);
            a.setSubmittedBy(submittedBy);
            a.setSubmittedAt(ZonedDateTime.now());
        }
        List<CourseAtr> saved = courseAtrRepository.saveAll(atrs);

        if (approvalService != null) {
            ProgrammeBatchCourse pbc = programmeBatchCourseRepository.findById(targetOfferingId).orElse(null);
            String courseName = pbc != null ? pbc.getEffectiveCourseName() : "Course ATR";
            try {
                approvalService.submitApprovalRequest(ApprovalRequest.builder()
                        .type(ApprovalType.COURSE_ATR)
                        .title("Course ATR for " + courseName)
                        .programmeBatchCourseId(targetOfferingId)
                        .resourceId(targetOfferingId)
                        .masterCourseId(pbc != null ? pbc.getMasterCourseId() : null)
                        .programmeBatchId(pbc != null ? pbc.getProgrammeBatchId() : null)
                        .submittedBy(submittedBy)
                        .build());
            } catch (Exception e) {
                System.out.println("[AtrService] Approval request sync log: " + e.getMessage());
            }
        }

        return saved.isEmpty() ? null : saved.get(0);
    }

    // =========================================================================
    //  STANDARD PROGRAMME ATR REPORT (OFFICIAL STRUCTURE)
    // =========================================================================

    @Transactional(readOnly = true)
    public ProgrammeAtrReportDto getProgrammeAtrReport(String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AtrService] getProgrammeAtrReport called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        
        ProgrammeBatch batch = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(programmeBatchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Programme Batch not found: " + programmeBatchId));
        }

        final String effectiveMasterProgrammeId = (masterProgrammeId != null && !masterProgrammeId.isBlank())
                ? masterProgrammeId
                : (batch != null ? batch.getMasterProgrammeId() : null);

        MasterProgramme prog = masterProgrammeRepository.findByIdAndDeletedAtIsNull(effectiveMasterProgrammeId)
                .orElseThrow(() -> new ResourceNotFoundException("Master Programme not found: " + effectiveMasterProgrammeId));

        if (batch == null) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeIdOrderByStartYearDesc(prog.getId());
            if (!batches.isEmpty()) {
                batch = batches.get(0);
            } else {
                throw new ResourceNotFoundException("No Programme Batches found for Master Programme: " + prog.getId());
            }
        }

        enforceProgrammeScope(prog.getId());
        enforceBatchScope(batch.getId());

        List<ProgrammeOutcome> pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(batch.getId());
        if (pos.isEmpty()) {
            pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(prog.getId());
        }
        Map<String, String> poStatementMap = new HashMap<>();
        Map<String, BigDecimal> poTargetMap = new HashMap<>();
        for (ProgrammeOutcome po : pos) {
            if (po.getCode() != null) {
                if (po.getStatement() != null && !po.getStatement().isBlank()) {
                    poStatementMap.put(po.getCode().toUpperCase(), po.getStatement());
                }
                if (po.getTarget() != null) {
                    poTargetMap.put(po.getCode().toUpperCase(), po.getTarget());
                }
            }
        }

        List<ProgrammeSpecificOutcome> psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(batch.getId());
        if (psos.isEmpty()) {
            psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(prog.getId());
        }
        Map<String, String> psoStatementMap = new HashMap<>();
        Map<String, BigDecimal> psoTargetMap = new HashMap<>();
        for (ProgrammeSpecificOutcome pso : psos) {
            if (pso.getCode() != null) {
                if (pso.getStatement() != null && !pso.getStatement().isBlank()) {
                    psoStatementMap.put(pso.getCode().toUpperCase(), pso.getStatement());
                }
                if (pso.getTarget() != null) {
                    psoTargetMap.put(pso.getCode().toUpperCase(), pso.getTarget());
                }
            }
        }

        ProgrammeAttainmentResultDto attainment = null;
        try {
            attainment = attainmentCalculationService.calculateProgrammeAttainment(prog.getId(), batch.getId());
        } catch (Exception ignored) {}

        Map<String, ProgrammeAttainmentResultDto.OutcomeAttainmentItem> poAttainmentMap = new HashMap<>();
        if (attainment != null && attainment.getOverallAttainment() != null && attainment.getOverallAttainment().getPos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : attainment.getOverallAttainment().getPos()) {
                String code = it.getOutcomeCode() != null ? it.getOutcomeCode() : (it.getPoCode() != null ? it.getPoCode() : "");
                if (!code.isBlank()) poAttainmentMap.put(code.toUpperCase(), it);
            }
        }

        Map<String, ProgrammeAttainmentResultDto.OutcomeAttainmentItem> psoAttainmentMap = new HashMap<>();
        if (attainment != null && attainment.getOverallAttainment() != null && attainment.getOverallAttainment().getPsos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : attainment.getOverallAttainment().getPsos()) {
                String code = it.getOutcomeCode() != null ? it.getOutcomeCode() : (it.getPsoCode() != null ? it.getPsoCode() : "");
                if (!code.isBlank()) psoAttainmentMap.put(code.toUpperCase(), it);
            }
        }

        Optional<ProgrammeAtr> existingAtr = programmeAtrRepository.findByProgrammeBatchId(batch.getId());

        Map<String, List<String>> savedPoActions = new HashMap<>();
        Map<String, List<String>> savedPsoActions = new HashMap<>();
        List<ProgrammeAtrReportDto.OutcomeRow> extraSavedPoRows = new ArrayList<>();
        List<ProgrammeAtrReportDto.OutcomeRow> extraSavedPsoRows = new ArrayList<>();

        if (existingAtr.isPresent() && existingAtr.get().getObservationsJson() != null && !existingAtr.get().getObservationsJson().isBlank()) {
            try {
                ProgrammeAtrReportDto savedDto = objectMapper.readValue(existingAtr.get().getObservationsJson(), ProgrammeAtrReportDto.class);
                if (savedDto != null) {
                    if (savedDto.getPoOutcomes() != null) {
                        for (ProgrammeAtrReportDto.OutcomeRow r : savedDto.getPoOutcomes()) {
                            if (r.getOutcomeCode() != null) {
                                String codeUpper = r.getOutcomeCode().toUpperCase();
                                if (r.getActions() != null) savedPoActions.put(codeUpper, r.getActions());
                                extraSavedPoRows.add(r);
                            }
                        }
                    }
                    if (savedDto.getPsoOutcomes() != null) {
                        for (ProgrammeAtrReportDto.OutcomeRow r : savedDto.getPsoOutcomes()) {
                            if (r.getOutcomeCode() != null) {
                                String codeUpper = r.getOutcomeCode().toUpperCase();
                                if (r.getActions() != null) savedPsoActions.put(codeUpper, r.getActions());
                                extraSavedPsoRows.add(r);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        List<ProgrammeAtrReportDto.OutcomeRow> poRows = new ArrayList<>();
        Set<String> processedPoCodes = new HashSet<>();

        if (!pos.isEmpty()) {
            for (ProgrammeOutcome po : pos) {
                String code = po.getCode() != null ? po.getCode() : "";
                String codeUpper = code.toUpperCase();
                processedPoCodes.add(codeUpper);

                String statement = po.getStatement() != null && !po.getStatement().isBlank()
                        ? po.getStatement()
                        : poStatementMap.getOrDefault(codeUpper, "Programme Outcome " + code);

                ProgrammeAttainmentResultDto.OutcomeAttainmentItem attItem = poAttainmentMap.get(codeUpper);

                BigDecimal target = po.getTarget() != null
                        ? po.getTarget()
                        : (attItem != null && attItem.getTarget() != null ? attItem.getTarget() : new BigDecimal("2.50"));

                BigDecimal attainmentLevel = attItem != null && attItem.getOverallAttainment() != null
                        ? attItem.getOverallAttainment()
                        : BigDecimal.ZERO;

                BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                        ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                poRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(savedPoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        } else if (!poAttainmentMap.isEmpty()) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : attainment.getOverallAttainment().getPos()) {
                String code = it.getOutcomeCode() != null ? it.getOutcomeCode() : (it.getPoCode() != null ? it.getPoCode() : "");
                String codeUpper = code.toUpperCase();
                processedPoCodes.add(codeUpper);

                String statement = it.getOutcomeStatement() != null && !it.getOutcomeStatement().isBlank()
                        ? it.getOutcomeStatement()
                        : poStatementMap.getOrDefault(codeUpper, "Programme Outcome " + code);

                BigDecimal target = it.getTarget() != null ? it.getTarget() : poTargetMap.getOrDefault(codeUpper, new BigDecimal("2.50"));
                BigDecimal attainmentLevel = it.getOverallAttainment() != null ? it.getOverallAttainment() : BigDecimal.ZERO;
                BigDecimal pct = it.getAchievementPercentage() != null ? it.getAchievementPercentage() : (target.compareTo(BigDecimal.ZERO) > 0 ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

                poRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(savedPoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        }

        for (ProgrammeAtrReportDto.OutcomeRow r : extraSavedPoRows) {
            String code = r.getOutcomeCode() != null ? r.getOutcomeCode() : "";
            String codeUpper = code.toUpperCase();
            if (!processedPoCodes.contains(codeUpper)) {
                processedPoCodes.add(codeUpper);
                String statement = r.getOutcomeStatement() != null && !r.getOutcomeStatement().isBlank()
                        ? r.getOutcomeStatement()
                        : poStatementMap.getOrDefault(codeUpper, "Programme Outcome " + code);

                ProgrammeAttainmentResultDto.OutcomeAttainmentItem attItem = poAttainmentMap.get(codeUpper);
                BigDecimal target = r.getTargetLevel() != null ? r.getTargetLevel() : (attItem != null && attItem.getTarget() != null ? attItem.getTarget() : poTargetMap.getOrDefault(codeUpper, new BigDecimal("2.50")));
                BigDecimal attainmentLevel = r.getAttainmentLevel() != null ? r.getAttainmentLevel() : (attItem != null && attItem.getOverallAttainment() != null ? attItem.getOverallAttainment() : BigDecimal.ZERO);
                BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0 ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                poRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(r.getActions() != null ? r.getActions() : Collections.emptyList())
                        .build());
            }
        }

        if (poRows.isEmpty()) {
            for (int i = 1; i <= 12; i++) {
                String code = "PO" + i;
                String codeUpper = code.toUpperCase();
                poRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement("Programme Outcome " + i)
                        .targetLevel(new BigDecimal("2.50"))
                        .attainmentLevel(BigDecimal.ZERO)
                        .achievementPercentage(BigDecimal.ZERO)
                        .actions(savedPoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        }

        List<ProgrammeAtrReportDto.OutcomeRow> psoRows = new ArrayList<>();
        Set<String> processedPsoCodes = new HashSet<>();

        if (!psos.isEmpty()) {
            for (ProgrammeSpecificOutcome pso : psos) {
                String code = pso.getCode() != null ? pso.getCode() : "";
                String codeUpper = code.toUpperCase();
                processedPsoCodes.add(codeUpper);

                String statement = pso.getStatement() != null && !pso.getStatement().isBlank()
                        ? pso.getStatement()
                        : psoStatementMap.getOrDefault(codeUpper, "Programme Specific Outcome " + code);

                ProgrammeAttainmentResultDto.OutcomeAttainmentItem attItem = psoAttainmentMap.get(codeUpper);

                BigDecimal target = pso.getTarget() != null
                        ? pso.getTarget()
                        : (attItem != null && attItem.getTarget() != null ? attItem.getTarget() : new BigDecimal("2.50"));

                BigDecimal attainmentLevel = attItem != null && attItem.getOverallAttainment() != null
                        ? attItem.getOverallAttainment()
                        : BigDecimal.ZERO;

                BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                        ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                psoRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(savedPsoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        } else if (!psoAttainmentMap.isEmpty()) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : attainment.getOverallAttainment().getPsos()) {
                String code = it.getOutcomeCode() != null ? it.getOutcomeCode() : (it.getPsoCode() != null ? it.getPsoCode() : "");
                String codeUpper = code.toUpperCase();
                processedPsoCodes.add(codeUpper);

                String statement = it.getOutcomeStatement() != null && !it.getOutcomeStatement().isBlank()
                        ? it.getOutcomeStatement()
                        : psoStatementMap.getOrDefault(codeUpper, "Programme Specific Outcome " + code);

                BigDecimal target = it.getTarget() != null ? it.getTarget() : psoTargetMap.getOrDefault(codeUpper, new BigDecimal("2.50"));
                BigDecimal attainmentLevel = it.getOverallAttainment() != null ? it.getOverallAttainment() : BigDecimal.ZERO;
                BigDecimal pct = it.getAchievementPercentage() != null ? it.getAchievementPercentage() : (target.compareTo(BigDecimal.ZERO) > 0 ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

                psoRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(savedPsoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        }

        for (ProgrammeAtrReportDto.OutcomeRow r : extraSavedPsoRows) {
            String code = r.getOutcomeCode() != null ? r.getOutcomeCode() : "";
            String codeUpper = code.toUpperCase();
            if (!processedPsoCodes.contains(codeUpper)) {
                processedPsoCodes.add(codeUpper);
                String statement = r.getOutcomeStatement() != null && !r.getOutcomeStatement().isBlank()
                        ? r.getOutcomeStatement()
                        : psoStatementMap.getOrDefault(codeUpper, "Programme Specific Outcome " + code);

                ProgrammeAttainmentResultDto.OutcomeAttainmentItem attItem = psoAttainmentMap.get(codeUpper);
                BigDecimal target = r.getTargetLevel() != null ? r.getTargetLevel() : (attItem != null && attItem.getTarget() != null ? attItem.getTarget() : psoTargetMap.getOrDefault(codeUpper, new BigDecimal("2.50")));
                BigDecimal attainmentLevel = r.getAttainmentLevel() != null ? r.getAttainmentLevel() : (attItem != null && attItem.getOverallAttainment() != null ? attItem.getOverallAttainment() : BigDecimal.ZERO);
                BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0 ? attainmentLevel.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                psoRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement(statement)
                        .targetLevel(target)
                        .attainmentLevel(attainmentLevel)
                        .achievementPercentage(pct)
                        .actions(r.getActions() != null ? r.getActions() : Collections.emptyList())
                        .build());
            }
        }

        if (psoRows.isEmpty()) {
            for (int i = 1; i <= 2; i++) {
                String code = "PSO" + i;
                String codeUpper = code.toUpperCase();
                psoRows.add(ProgrammeAtrReportDto.OutcomeRow.builder()
                        .outcomeCode(code)
                        .outcomeStatement("Programme Specific Outcome " + i)
                        .targetLevel(new BigDecimal("2.50"))
                        .attainmentLevel(BigDecimal.ZERO)
                        .achievementPercentage(BigDecimal.ZERO)
                        .actions(savedPsoActions.getOrDefault(codeUpper, Collections.emptyList()))
                        .build());
            }
        }

        String batchStatusStr = batch.getStatus() != null ? batch.getStatus().trim().toUpperCase() : "ACTIVE";
        boolean isCompleted = "COMPLETED".equals(batchStatusStr);
        boolean isGraduated = "GRADUATED".equals(batchStatusStr);
        boolean isConcluded = isCompleted || isGraduated;

        boolean isUnlocked;
        String unlockReason;
        String defaultStatus;

        if (isCompleted) {
            isUnlocked = true;
            unlockReason = "Programme batch has been completed by HOD. Programme ATR is unlocked for editing and submission.";
            defaultStatus = "DRAFT";
        } else if (isGraduated) {
            isUnlocked = false;
            unlockReason = "Programme batch is GRADUATED. All attainment, survey, and Programme ATR records are permanently locked.";
            defaultStatus = "APPROVED";
        } else {
            isUnlocked = false;
            unlockReason = "Programme ATR is locked. The HOD has not marked this programme batch as COMPLETED or GRADUATED yet. (Current Batch Status: " + batchStatusStr + ")";
            defaultStatus = "LOCKED_PENDING_COMPLETION";
        }

        String status = existingAtr.map(a -> a.getStatus() != null ? a.getStatus().name() : defaultStatus)
                .orElse(defaultStatus);
        String patrId = existingAtr.map(ProgrammeAtr::getId).orElse(null);

        return ProgrammeAtrReportDto.builder()
                .reportType("PROGRAMME_ATR")
                .programmeAtrId(patrId)
                .programme(ProgrammeAtrReportDto.ProgrammeSummary.builder().id(prog.getId()).code(prog.getCode()).name(prog.getName()).build())
                .batch(ProgrammeAtrReportDto.BatchSummary.builder()
                        .id(batch.getId())
                        .name(batch.getName())
                        .startYear(batch.getStartYear() != null ? String.valueOf(batch.getStartYear()) : "")
                        .endYear(batch.getEndYear() != null ? String.valueOf(batch.getEndYear()) : "")
                        .status(batchStatusStr)
                        .build())
                .poOutcomes(poRows)
                .psoOutcomes(psoRows)
                .status(status)
                .isUnlocked(isUnlocked)
                .unlockReason(unlockReason)
                .batchStatus(batchStatusStr)
                .build();
    }

    @Transactional
    public ProgrammeAtrReportDto saveProgrammeAtrReport(ProgrammeAtrReportDto dto) {
        System.out.println("[AtrService] saveProgrammeAtrReport called | masterProgrammeId: " + (dto != null && dto.getProgramme() != null ? dto.getProgramme().getId() : "null"));
        if (dto == null || dto.getBatch() == null || dto.getBatch().getId() == null || dto.getBatch().getId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme batch is required for Programme ATR.");
        }
        String requestedBatchId = dto.getBatch().getId();
        ProgrammeBatch batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(requestedBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(requestedBatchId.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("Programme Batch not found: " + requestedBatchId));
        
        final String targetBatchId = batch.getId();
        final String progId = batch.getMasterProgrammeId();
        enforceProgrammeScope(progId);
        enforceBatchScope(targetBatchId);

        String bStatus = batch.getStatus() != null ? batch.getStatus().trim().toUpperCase() : "ACTIVE";
        if ("ACTIVE".equalsIgnoreCase(bStatus) || "INITIALIZED".equalsIgnoreCase(bStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Programme ATR is locked. Programme batch '" + batch.getName() + "' has not been completed by the HOD yet. (Current status: " + bStatus + ")");
        }
        if ("GRADUATED".equalsIgnoreCase(bStatus)) {
            if (batch.getEditingWindowUntil() == null || batch.getEditingWindowUntil().isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify Programme ATR: Programme batch '" + batch.getName() + "' is GRADUATED and permanently locked.");
            }
        }

        if (approvalService != null && approvalService.isProgrammeAtrApproved(targetBatchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Programme ATR. A revision must be requested first.");
        }

        if (dto.getProgramme() == null) {
            dto.setProgramme(ProgrammeAtrReportDto.ProgrammeSummary.builder().id(progId).name(batch.getProgrammeName()).code(batch.getProgrammeCode()).build());
        } else {
            dto.getProgramme().setId(progId);
            dto.getProgramme().setName(batch.getProgrammeName());
            dto.getProgramme().setCode(batch.getProgrammeCode());
        }
        dto.getBatch().setId(targetBatchId);
        dto.getBatch().setName(batch.getName());
        dto.getBatch().setStartYear(batch.getStartYear() != null ? String.valueOf(batch.getStartYear()) : "");
        dto.getBatch().setEndYear(batch.getEndYear() != null ? String.valueOf(batch.getEndYear()) : "");
        dto.setReportType("PROGRAMME_ATR");

        ProgrammeAtr atr = programmeAtrRepository.findByProgrammeBatchId(targetBatchId)
                .orElseGet(() -> ProgrammeAtr.builder()
                        .id("patr-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchId(targetBatchId)
                        .build());

        if (atr.getStatus() == ProgrammeAtrStatus.APPROVED || atr.getStatus() == ProgrammeAtrStatus.SUBMITTED_FOR_VERIFICATION || (approvalService != null && approvalService.isProgrammeAtrApproved(targetBatchId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot modify submitted or approved Programme ATR. A revision must be requested first.");
        }

        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            try {
                atr.setStatus(ProgrammeAtrStatus.valueOf(dto.getStatus()));
            } catch (Exception ignored) {
                atr.setStatus(ProgrammeAtrStatus.DRAFT);
            }
        } else if (atr.getStatus() == null) {
            atr.setStatus(ProgrammeAtrStatus.DRAFT);
        }

        dto.setProgrammeAtrId(atr.getId());
        dto.setStatus(atr.getStatus().name());

        try {
            atr.setObservationsJson(objectMapper.writeValueAsString(dto));
        } catch (Exception ignored) {}

        atr.setUpdatedAt(ZonedDateTime.now());
        programmeAtrRepository.save(atr);

        return getProgrammeAtrReport(progId, targetBatchId);
    }

    @Transactional
    public ProgrammeAtr submitProgrammeAtr(String masterProgrammeId, String programmeBatchId, String submittedBy) {
        System.out.println("[AtrService] submitProgrammeAtr called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId + " | submittedBy: " + submittedBy);
        
        ProgrammeBatch batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("Programme Batch not found: " + programmeBatchId));
        final String targetBatchId = batch.getId();
        final String progId = batch.getMasterProgrammeId();
        
        enforceProgrammeScope(progId);
        enforceBatchScope(targetBatchId);

        String bStatus = batch.getStatus() != null ? batch.getStatus().trim().toUpperCase() : "ACTIVE";
        if ("ACTIVE".equalsIgnoreCase(bStatus) || "INITIALIZED".equalsIgnoreCase(bStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Programme ATR is locked. Programme batch '" + batch.getName() + "' has not been completed by the HOD yet. (Current status: " + bStatus + ")");
        }
        if ("GRADUATED".equalsIgnoreCase(bStatus)) {
            if (batch.getEditingWindowUntil() == null || batch.getEditingWindowUntil().isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot submit Programme ATR: Programme batch '" + batch.getName() + "' is GRADUATED and permanently locked.");
            }
        }

        ProgrammeAtr atr = programmeAtrRepository.findByProgrammeBatchId(targetBatchId)
                .orElseGet(() -> {
                    ProgrammeAtrReportDto initialDto = getProgrammeAtrReport(progId, targetBatchId);
                    ProgrammeAtr created = ProgrammeAtr.builder()
                            .id("patr-" + UUID.randomUUID().toString().substring(0, 8))
                            .programmeBatchId(targetBatchId)
                            .status(ProgrammeAtrStatus.DRAFT)
                            .build();
                    try {
                        created.setObservationsJson(objectMapper.writeValueAsString(initialDto));
                    } catch (Exception ignored) {}
                    return created;
                });

        CurrentUserScope scope = getScope();
        String authoritativeSubmitter = (scope != null && scope.getEmail() != null)
                ? scope.getEmail()
                : (scope != null && scope.getUsername() != null ? scope.getUsername() : submittedBy);

        atr.setStatus(ProgrammeAtrStatus.SUBMITTED_FOR_VERIFICATION);
        atr.setSubmittedBy(authoritativeSubmitter);
        atr.setSubmittedAt(ZonedDateTime.now());
        atr.setUpdatedAt(ZonedDateTime.now());

        if (atr.getObservationsJson() != null && !atr.getObservationsJson().isBlank()) {
            try {
                ProgrammeAtrReportDto dto = objectMapper.readValue(atr.getObservationsJson(), ProgrammeAtrReportDto.class);
                if (dto != null) {
                    dto.setStatus(ProgrammeAtrStatus.SUBMITTED_FOR_VERIFICATION.name());
                    dto.setProgrammeAtrId(atr.getId());
                    atr.setObservationsJson(objectMapper.writeValueAsString(dto));
                }
            } catch (Exception ignored) {}
        }
        ProgrammeAtr saved = programmeAtrRepository.save(atr);

        if (approvalService != null) {
            ApprovalRequest req = ApprovalRequest.builder()
                    .id("app-" + UUID.randomUUID().toString().substring(0, 8))
                    .type(ApprovalType.PROGRAMME_ATR)
                    .title("Programme ATR for " + batch.getName())
                    .resourceId(programmeBatchId)
                    .masterProgrammeId(progId)
                    .programmeBatchId(programmeBatchId)
                    .status(ApprovalStatus.PENDING)
                    .submittedBy(authoritativeSubmitter)
                    .submittedAt(ZonedDateTime.now())
                    .build();
            approvalService.submitApprovalRequest(req);
        }

        return saved;
    }

    // =========================================================================
    //  HISTORICAL BATCH COMPARISON
    // =========================================================================

    @Transactional(readOnly = true)
    public BatchComparisonDto getProgrammeBatchComparison(String masterProgrammeId, List<String> programmeBatchIds) {
        System.out.println("[AtrService] getProgrammeBatchComparison called | masterProgrammeId: " + masterProgrammeId);
        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            enforceProgrammeScope(masterProgrammeId);
        }
        if (programmeBatchIds != null) {
            for (String bId : programmeBatchIds) {
                if (bId != null && !bId.isBlank()) {
                    enforceBatchScope(bId);
                }
            }
        }
        List<ProgrammeBatch> batches = (programmeBatchIds != null && !programmeBatchIds.isEmpty())
                ? programmeBatchRepository.findAllById(programmeBatchIds)
                : programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);

        List<BatchComparisonDto.BatchAttainmentItem> items = new ArrayList<>();
        for (ProgrammeBatch b : batches) {
            ProgrammeAttainmentResultDto res = attainmentCalculationService.calculateProgrammeAttainment(masterProgrammeId, b.getId());
            Optional<ProgrammeAtr> patr = programmeAtrRepository.findByProgrammeBatchId(b.getId());

            Map<String, BigDecimal> poMap = new LinkedHashMap<>();
            Map<String, BigDecimal> psoMap = new LinkedHashMap<>();

            if (res.getOverallAttainment() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPos()) {
                    poMap.put(it.getPoCode(), it.getOverallAttainment());
                }
                for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPsos()) {
                    psoMap.put(it.getPsoCode(), it.getOverallAttainment());
                }
            }

            items.add(BatchComparisonDto.BatchAttainmentItem.builder()
                    .programmeBatchId(b.getId())
                    .batchName(b.getName())
                    .programmeAtrStatus(patr.map(a -> a.getStatus().name()).orElse("DRAFT"))
                    .poAttainment(poMap)
                    .psoAttainment(psoMap)
                    .build());
        }

        return BatchComparisonDto.builder()
                .masterProgrammeId(masterProgrammeId)
                .batches(items)
                .build();
    }
    @Transactional(readOnly = true)
    public List<CourseAtrReportDto> getHistoricalCourseAtrs(String courseOrOfferingId) {
        System.out.println("[AtrService] getHistoricalCourseAtrs called | courseOrOfferingId: " + courseOrOfferingId);
        List<CourseAtrReportDto> historicalReports = new ArrayList<>();

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(courseOrOfferingId).orElse(null);
        if (offering != null) {
            String courseCode = offering.getCode() != null && !offering.getCode().isBlank() ? offering.getCode() : offering.getCourseCode();
            String rootCode = (courseCode != null && courseCode.contains("-")) ? courseCode.substring(0, courseCode.indexOf('-')) : courseCode;
            ProgrammeBatch batch = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
            String progId = batch != null ? batch.getMasterProgrammeId() : offering.getMasterProgrammeId();
            if (progId != null) {
                List<ProgrammeBatch> allBatches = programmeBatchRepository.findByMasterProgrammeIdOrderByStartYearDesc(progId);
                for (ProgrammeBatch b : allBatches) {
                    List<ProgrammeBatchCourse> pbcList = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(b.getId());
                    for (ProgrammeBatchCourse pbc : pbcList) {
                        String pbcCode = pbc.getCourseCode();
                        boolean matches = (courseCode != null && courseCode.equalsIgnoreCase(pbcCode))
                                || (rootCode != null && pbcCode != null && pbcCode.toUpperCase().startsWith(rootCode.toUpperCase()))
                                || (pbc.getMasterCourseId() != null && pbc.getMasterCourseId().equalsIgnoreCase(courseOrOfferingId));
                        if (matches) {
                            List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(pbc.getId());
                            if (!atrs.isEmpty()) {
                                CourseAtrReportDto report = buildCourseAtrReport(pbc);
                                if (report != null && historicalReports.stream().noneMatch(r -> r.getCourseOffering().getId().equals(report.getCourseOffering().getId()))) {
                                    historicalReports.add(report);
                                }
                            }
                        }
                    }
                }
                if (!historicalReports.isEmpty()) {
                    return historicalReports;
                }
            }
        }

        // Fallback for legacy masterCourseId
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(courseOrOfferingId);
        for (ProgrammeBatchCourse off : offerings) {
            List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(off.getId());
            if (!atrs.isEmpty()) {
                CourseAtrReportDto report = buildCourseAtrReport(off);
                if (report != null && historicalReports.stream().noneMatch(r -> r.getCourseOffering().getId().equals(report.getCourseOffering().getId()))) {
                    historicalReports.add(report);
                }
            }
        }
        return historicalReports;
    }

    @Transactional(readOnly = true)
    public CourseAtrReportDto getPreviousBatchCourseAtrReport(String programmeBatchCourseId) {
        System.out.println("[AtrService] getPreviousBatchCourseAtrReport called | programmeBatchCourseId: " + programmeBatchCourseId);
        String offeringId = resolveOfferingId(programmeBatchCourseId);
        if (offeringId == null || offeringId.isBlank()) return null;
        enforceOfferingScope(offeringId);

        ProgrammeBatchCourse currentOffering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
        if (currentOffering == null || currentOffering.getProgrammeBatchId() == null) return null;

        String currentCode = currentOffering.getCourseCode();
        if (currentCode == null || currentCode.isBlank()) {
            currentCode = currentOffering.getCode();
        }

        ProgrammeBatch currentBatch = programmeBatchRepository.findById(currentOffering.getProgrammeBatchId()).orElse(null);
        if (currentBatch == null || currentBatch.getMasterProgrammeId() == null || currentBatch.getStartYear() == null) return null;

        int targetStartYear = currentBatch.getStartYear() - 1;

        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeIdOrderByStartYearDesc(currentBatch.getMasterProgrammeId());

        // 1. Try to find exact previous batch with (startYear - 1)
        ProgrammeBatch prevBatch = batches.stream()
                .filter(b -> b.getStartYear() != null && b.getStartYear() == targetStartYear)
                .findFirst()
                .orElse(null);

        if (prevBatch != null && currentCode != null) {
            final String codeToMatch = currentCode.trim();
            List<ProgrammeBatchCourse> prevOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(prevBatch.getId());
            for (ProgrammeBatchCourse prevOffering : prevOfferings) {
                if (codeToMatch.equalsIgnoreCase(prevOffering.getCourseCode()) || (prevOffering.getMasterCourseId() != null && prevOffering.getMasterCourseId().equals(currentOffering.getMasterCourseId()))) {
                    List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(prevOffering.getId());
                    if (!atrs.isEmpty()) {
                        return buildCourseAtrReport(prevOffering);
                    }
                }
            }
        }

        // 2. Fallback to nearest previous batch if exact startYear - 1 had no ATR
        for (ProgrammeBatch b : batches) {
            if (b.getStartYear() != null && b.getStartYear() < currentBatch.getStartYear()) {
                List<ProgrammeBatchCourse> prevOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(b.getId());
                for (ProgrammeBatchCourse prevOffering : prevOfferings) {
                    if (currentCode != null && (currentCode.trim().equalsIgnoreCase(prevOffering.getCourseCode()) || (prevOffering.getMasterCourseId() != null && prevOffering.getMasterCourseId().equals(currentOffering.getMasterCourseId())))) {
                        List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(prevOffering.getId());
                        if (!atrs.isEmpty()) {
                            return buildCourseAtrReport(prevOffering);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeAtrReportDto> getHistoricalProgrammeAtrs(String masterProgrammeId) {
        System.out.println("[AtrService] getHistoricalProgrammeAtrs called | masterProgrammeId: " + masterProgrammeId);
        enforceProgrammeScope(masterProgrammeId);
        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeIdOrderByStartYearDesc(masterProgrammeId);
        List<ProgrammeAtrReportDto> historicalReports = new ArrayList<>();
        for (ProgrammeBatch batch : batches) {
            Optional<ProgrammeAtr> atr = programmeAtrRepository.findByProgrammeBatchId(batch.getId());
            if (atr.isPresent()) {
                ProgrammeAtrReportDto report = getProgrammeAtrReport(masterProgrammeId, batch.getId());
                if (report != null) {
                    historicalReports.add(report);
                }
            }
        }
        return historicalReports;
    }

    private boolean isAtrLocked(ProgrammeAtrStatus status) {
        if (status == null) return false;
        return status == ProgrammeAtrStatus.SUBMITTED
                || status == ProgrammeAtrStatus.SUBMITTED_FOR_VERIFICATION
                || status == ProgrammeAtrStatus.PENDING_APPROVAL
                || status == ProgrammeAtrStatus.VERIFIED
                || status == ProgrammeAtrStatus.APPROVED;
    }

}
