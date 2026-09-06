package com.dypiu.nba.service;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.dto.ProgrammeTargetDto;
import com.dypiu.nba.dto.CourseMappingMatrixDto;
import com.dypiu.nba.exception.ResourceNotFoundException;
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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutcomeService {

    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeOutcomeRepository poRepository;
    private final ProgrammeSpecificOutcomeRepository psoRepository;
    private final PeoOutcomeRepository peoRepository;
    private final CourseOutcomeRepository coRepository;
    private final PoCompetencyRepository poCompetencyRepository;
    private final PsoCompetencyRepository psoCompetencyRepository;
    private final MasterCourseRepository masterCourseRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final CoPoMappingRepository coPoMappingRepository;
    private final CoPsoMappingRepository coPsoMappingRepository;
    private final CourseMappingKeywordRepository courseMappingKeywordRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;
    private final CurrentUserScopeService currentUserScopeService;
    private final AuditLogService auditLogService;
    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final BatchLifecycleService batchLifecycleService;
    private final ObjectMapper objectMapper;

    private static final Comparator<String> NATURAL_CODE_COMPARATOR = (c1, c2) -> {
        if (c1 == null) return -1;
        if (c2 == null) return 1;
        String p1 = c1.replaceAll("\\D+", "");
        String p2 = c2.replaceAll("\\D+", "");
        if (!p1.isEmpty() && !p2.isEmpty()) {
            try {
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } catch (NumberFormatException ignored) {}
        }
        return c1.compareToIgnoreCase(c2);
    };

    private final Map<String, Map<String, Object>> coursePoKeywordsMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> coursePsoKeywordsMap = new java.util.concurrent.ConcurrentHashMap<>();

    private CurrentUserScope getScope() {
        // Business APIs are authenticated in SecurityConfig.  Do not turn a
        // missing/invalid identity into an unrestricted (null) scope.
        return currentUserScopeService.getCurrentUserScope();
    }

    /**
     * Editing course-level evidence is reserved for the coordinator assigned
     * to the exact offering.  IDs in the body are intentionally ignored.
     */
    private void enforceCourseCoordinatorMutation(String courseIdOrOfferingId) {
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isIqac()) return;
        String offeringId = resolveOfferingId(courseIdOrOfferingId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
        
        String coordEmail = offering.getCourseCoordinatorEmail() != null ? offering.getCourseCoordinatorEmail() : offering.getCoordinatorEmail();
        boolean isAssignedCourseCoordinator = (offering.getCourseCoordinatorId() != null && scope.getUserId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()))
                || (coordEmail != null && scope.getEmail() != null && !coordEmail.isBlank() && !scope.getEmail().isBlank() && coordEmail.trim().equalsIgnoreCase(scope.getEmail().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getName() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getName().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getName().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getUsername() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getUsername().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getUsername().trim()))
                || (offering.getCourseCoordinatorName() != null && scope.getEmail() != null && !offering.getCourseCoordinatorName().isBlank() && !scope.getEmail().isBlank() && offering.getCourseCoordinatorName().trim().equalsIgnoreCase(scope.getEmail().trim()))
                || (offering.getAssignedFaculty() != null && scope.getEmail() != null && !scope.getEmail().isBlank() && offering.getAssignedFaculty().toLowerCase().contains(scope.getEmail().trim().toLowerCase()))
                || (offering.getAssignedFaculty() != null && scope.getName() != null && !scope.getName().isBlank() && offering.getAssignedFaculty().toLowerCase().contains(scope.getName().trim().toLowerCase()))
                || (offering.getAssignedFaculty() != null && scope.getUsername() != null && !scope.getUsername().isBlank() && offering.getAssignedFaculty().toLowerCase().contains(scope.getUsername().trim().toLowerCase()));
        
        boolean isAssignedProgrammeCoordinator = false;
        if (scope.isProgrammeCoordinator()) {
            ProgrammeBatch batch = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
            if (batch != null) {
                if (scope.getUserId() != null && Objects.equals(batch.getCoordinatorId(), scope.getUserId())) {
                    isAssignedProgrammeCoordinator = true;
                } else if (scope.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim())) {
                    isAssignedProgrammeCoordinator = true;
                } else if (scope.getMasterProgrammeId() != null && Objects.equals(batch.getMasterProgrammeId(), scope.getMasterProgrammeId())) {
                    isAssignedProgrammeCoordinator = true;
                }
            }
        }
        
        if (!isAssignedCourseCoordinator && !isAssignedProgrammeCoordinator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: only the coordinator assigned to this course offering may modify it.");
        }
    }

    private void enforceProgrammeCoordinatorMutation(String programmeOrProgrammeBatchId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (scope.isDirector() || scope.isHod()) {
            enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
            return;
        }
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("Programme batch not found: " + programmeBatchId));
        
        boolean isAssignedBatchCoordinator = (scope.getUserId() != null && Objects.equals(batch.getCoordinatorId(), scope.getUserId()))
                || (scope.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim()));
        boolean isAssignedProgrammeCoordinator = scope.isProgrammeCoordinator() && 
                                               (isAssignedBatchCoordinator || (scope.getMasterProgrammeId() != null && Objects.equals(batch.getMasterProgrammeId(), scope.getMasterProgrammeId())));
        
        if (!isAssignedBatchCoordinator && !isAssignedProgrammeCoordinator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: only the coordinator assigned to this programme batch may modify its targets.");
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

    private void enforceBatchOrProgrammeScope(String programmeOrProgrammeBatchId) {
        if (programmeOrProgrammeBatchId == null || programmeOrProgrammeBatchId.isBlank()) return;
        if (programmeBatchRepository.existsById(programmeOrProgrammeBatchId)) {
            enforceBatchScope(programmeOrProgrammeBatchId);
        } else {
            java.util.Optional<com.dypiu.nba.entity.ProgrammeBatch> batchByName = programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeOrProgrammeBatchId.trim());
            if (batchByName.isPresent()) {
                enforceBatchScope(batchByName.get().getId());
            } else {
                enforceProgrammeScope(programmeOrProgrammeBatchId);
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
                    || (scope.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim()));
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
        String progId = null;
        if (offering.getMasterCourseId() != null) {
            MasterCourse c = masterCourseRepository.findById(offering.getMasterCourseId()).orElse(null);
            if (c != null && c.getMasterProgrammeId() != null) {
                progId = c.getMasterProgrammeId();
            }
        }
        if (progId == null && offering.getProgrammeBatchId() != null) {
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

    private void enforceCourseScope(String masterCourseId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (masterCourseId == null || masterCourseId.isBlank()) return;
        MasterCourse course = masterCourseRepository.findById(masterCourseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + masterCourseId));

        if (scope.isFaculty()) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
            boolean hasAssigned = offerings.stream().anyMatch(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                return isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
            });
            if (!hasAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this Course.");
            }
            boolean hasApproved = offerings.stream().anyMatch(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                boolean isFacultyAssigned = isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
                return isFacultyAssigned && isCourseAllocationApproved(o);
            });
            if (!hasApproved) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
            }
            return;
        }

        enforceProgrammeScope(course.getMasterProgrammeId());
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

    private void enforceOfferingEditability(String masterCourseIdOrOfferingId) {
        if (masterCourseIdOrOfferingId == null || masterCourseIdOrOfferingId.isBlank()) return;
        String offeringId = resolveOfferingId(masterCourseIdOrOfferingId);
        if (offeringId != null) {
            ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
            if (offering != null && offering.getProgrammeBatchId() != null) {
                batchLifecycleService.enforceBatchEditability(offering.getProgrammeBatchId());
            }
        }
    }

    private void enforceCourseOrOfferingScope(String masterCourseIdOrOfferingId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (masterCourseIdOrOfferingId == null || masterCourseIdOrOfferingId.isBlank()) return;
        if (programmeBatchCourseRepository.existsById(masterCourseIdOrOfferingId)) {
            enforceOfferingScope(masterCourseIdOrOfferingId);
        } else if (masterCourseRepository.existsById(masterCourseIdOrOfferingId)) {
            enforceCourseScope(masterCourseIdOrOfferingId);
        }
    }

    private String resolveProgrammeBatchId(String programmeOrProgrammeBatchId) {
        if (programmeOrProgrammeBatchId == null || programmeOrProgrammeBatchId.isBlank()) return null;
        if (programmeBatchRepository.existsById(programmeOrProgrammeBatchId)) {
            return programmeOrProgrammeBatchId;
        }
        java.util.Optional<com.dypiu.nba.entity.ProgrammeBatch> batchByName = programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeOrProgrammeBatchId.trim());
        if (batchByName.isPresent()) {
            return batchByName.get().getId();
        }
        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(programmeOrProgrammeBatchId);
        if (!batches.isEmpty()) {
            for (ProgrammeBatch b : batches) {
                if (!poRepository.findByProgrammeBatchId(b.getId()).isEmpty()) {
                    return b.getId();
                }
            }
            return batches.get(0).getId();
        }
        return programmeOrProgrammeBatchId;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeOutcome> getPOsByProgramme(String programmeOrProgrammeBatchId) {
        System.out.println("[OutcomeService] getPOsByProgramme called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            return Collections.emptyList();
        }
        List<ProgrammeOutcome> list = poRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        for (ProgrammeOutcome po : list) {
            List<PoCompetency> comps = poCompetencyRepository.findByPoIdOrderByCodeAsc(po.getId());
            comps.sort(Comparator.comparing(PoCompetency::getCode, NATURAL_CODE_COMPARATOR));
            po.setCompetencies(comps);
        }
        list.sort(Comparator.comparing(ProgrammeOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return list;
    }

    @Transactional
    public List<ProgrammeOutcome> savePOs(String programmeOrProgrammeBatchId, List<ProgrammeOutcome> pos) {
        System.out.println("[OutcomeService] savePOs called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId + " | count: " + (pos != null ? pos.size() : 0));
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new ResourceNotFoundException("Programme Batch not found: " + programmeOrProgrammeBatchId);
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);
        
        List<ProgrammeOutcome> existing = poRepository.findByProgrammeBatchId(programmeBatchId);
        Map<String, ProgrammeOutcome> existingByCode = existing.stream()
                .collect(Collectors.toMap(
                        p -> p.getCode().toLowerCase(),
                        p -> p,
                        (e1, e2) -> e1
                ));

        Set<String> processedIds = new HashSet<>();
        List<ProgrammeOutcome> toSave = new ArrayList<>();
        Map<String, List<PoCompetency>> competenciesMap = new HashMap<>();

        if (pos != null) {
            for (ProgrammeOutcome po : pos) {
                po.setProgrammeBatchId(programmeBatchId);

                String key = po.getCode().toLowerCase();
                ProgrammeOutcome targetPo;
                if (existingByCode.containsKey(key)) {
                    targetPo = existingByCode.get(key);
                    targetPo.setStatement(po.getStatement());
                    if (po.getTarget() != null) {
                        targetPo.setTarget(po.getTarget());
                    }
                } else {
                    targetPo = po;
                    if (targetPo.getId() == null || targetPo.getId().isBlank()) {
                        targetPo.setId("po-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                }

                competenciesMap.put(targetPo.getId(), po.getCompetencies());
                processedIds.add(targetPo.getId());
                toSave.add(targetPo);
            }
        }

        // Delete obsolete POs & their competencies
        List<ProgrammeOutcome> toDelete = existing.stream()
                .filter(p -> !processedIds.contains(p.getId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            for (ProgrammeOutcome delPo : toDelete) {
                poCompetencyRepository.deleteByPoId(delPo.getId());
            }
            poRepository.deleteAll(toDelete);
        }

        List<ProgrammeOutcome> saved = poRepository.saveAll(toSave);

        for (ProgrammeOutcome po : saved) {
            poCompetencyRepository.deleteByPoId(po.getId());
            poCompetencyRepository.flush();
            List<PoCompetency> rawComps = competenciesMap.get(po.getId());
            List<PoCompetency> compsToSave = new ArrayList<>();
            if (rawComps != null) {
                int cIdx = 1;
                for (PoCompetency c : rawComps) {
                    if (c.getStatement() == null || c.getStatement().isBlank()) continue;
                    String cCode = c.getCode();
                    if (cCode == null || cCode.isBlank()) {
                        cCode = po.getCode() + "." + cIdx;
                    }
                    String cId = c.getId();
                    if (cId == null || cId.isBlank() || cId.startsWith("comp-")) {
                        cId = "pocomp-" + UUID.randomUUID().toString().substring(0, 8);
                    }
                    cIdx++;
                    compsToSave.add(PoCompetency.builder()
                            .id(cId)
                            .poId(po.getId())
                            .code(cCode)
                            .statement(c.getStatement())
                            .build());
                }
            }
            if (!compsToSave.isEmpty()) {
                compsToSave.sort(Comparator.comparing(PoCompetency::getCode, NATURAL_CODE_COMPARATOR));
                poCompetencyRepository.saveAll(compsToSave);
                poCompetencyRepository.flush();
            }
            po.setCompetencies(compsToSave);
        }

        saved.sort(Comparator.comparing(ProgrammeOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeSpecificOutcome> getPSOsByProgramme(String programmeOrProgrammeBatchId) {
        System.out.println("[OutcomeService] getPSOsByProgramme called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            return Collections.emptyList();
        }
        List<ProgrammeSpecificOutcome> list = psoRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        for (ProgrammeSpecificOutcome pso : list) {
            List<PsoCompetency> comps = psoCompetencyRepository.findByPsoIdOrderByCodeAsc(pso.getId());
            comps.sort(Comparator.comparing(PsoCompetency::getCode, NATURAL_CODE_COMPARATOR));
            pso.setCompetencies(comps);
        }
        list.sort(Comparator.comparing(ProgrammeSpecificOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return list;
    }

    @Transactional
    public List<ProgrammeSpecificOutcome> savePSOs(String programmeOrProgrammeBatchId, List<ProgrammeSpecificOutcome> psos) {
        System.out.println("[OutcomeService] savePSOs called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId + " | count: " + (psos != null ? psos.size() : 0));
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new ResourceNotFoundException("Programme Batch not found: " + programmeOrProgrammeBatchId);
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);

        List<ProgrammeSpecificOutcome> existing = psoRepository.findByProgrammeBatchId(programmeBatchId);
        Map<String, ProgrammeSpecificOutcome> existingByCode = existing.stream()
                .collect(Collectors.toMap(
                        p -> p.getCode().toLowerCase(),
                        p -> p,
                        (e1, e2) -> e1
                ));

        Set<String> processedIds = new HashSet<>();
        List<ProgrammeSpecificOutcome> toSave = new ArrayList<>();
        Map<String, List<PsoCompetency>> competenciesMap = new HashMap<>();

        if (psos != null) {
            for (ProgrammeSpecificOutcome pso : psos) {
                pso.setProgrammeBatchId(programmeBatchId);

                String key = pso.getCode().toLowerCase();
                ProgrammeSpecificOutcome targetPso;
                if (existingByCode.containsKey(key)) {
                    targetPso = existingByCode.get(key);
                    targetPso.setStatement(pso.getStatement());
                    if (pso.getTarget() != null) {
                        targetPso.setTarget(pso.getTarget());
                    }
                } else {
                    targetPso = pso;
                    if (targetPso.getId() == null || targetPso.getId().isBlank()) {
                        targetPso.setId("pso-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                }

                competenciesMap.put(targetPso.getId(), pso.getCompetencies());
                processedIds.add(targetPso.getId());
                toSave.add(targetPso);
            }
        }

        List<ProgrammeSpecificOutcome> toDelete = existing.stream()
                .filter(p -> !processedIds.contains(p.getId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            for (ProgrammeSpecificOutcome delPso : toDelete) {
                psoCompetencyRepository.deleteByPsoId(delPso.getId());
            }
            psoRepository.deleteAll(toDelete);
        }

        List<ProgrammeSpecificOutcome> saved = psoRepository.saveAll(toSave);

        for (ProgrammeSpecificOutcome pso : saved) {
            psoCompetencyRepository.deleteByPsoId(pso.getId());
            psoCompetencyRepository.flush();
            List<PsoCompetency> rawComps = competenciesMap.get(pso.getId());
            List<PsoCompetency> compsToSave = new ArrayList<>();
            if (rawComps != null) {
                int cIdx = 1;
                for (PsoCompetency c : rawComps) {
                    if (c.getStatement() == null || c.getStatement().isBlank()) continue;
                    String cCode = c.getCode();
                    if (cCode == null || cCode.isBlank()) {
                        cCode = pso.getCode() + "." + cIdx;
                    }
                    String cId = c.getId();
                    if (cId == null || cId.isBlank() || cId.startsWith("comp-") || cId.startsWith("psocomp-")) {
                        cId = "psocomp-" + UUID.randomUUID().toString().substring(0, 8);
                    }
                    cIdx++;
                    compsToSave.add(PsoCompetency.builder()
                            .id(cId)
                            .psoId(pso.getId())
                            .code(cCode)
                            .statement(c.getStatement())
                            .build());
                }
            }
            if (!compsToSave.isEmpty()) {
                compsToSave.sort(Comparator.comparing(PsoCompetency::getCode, NATURAL_CODE_COMPARATOR));
                psoCompetencyRepository.saveAll(compsToSave);
                psoCompetencyRepository.flush();
            }
            pso.setCompetencies(compsToSave);
        }

        saved.sort(Comparator.comparing(ProgrammeSpecificOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PeoOutcome> getPEOsByProgramme(String programmeOrProgrammeBatchId) {
        System.out.println("[OutcomeService] getPEOsByProgramme called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            return Collections.emptyList();
        }
        List<PeoOutcome> list = peoRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        list.sort(Comparator.comparing(PeoOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return list;
    }

    @Transactional
    public List<PeoOutcome> savePEOs(String programmeOrProgrammeBatchId, List<PeoOutcome> peos) {
        System.out.println("[OutcomeService] savePEOs called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId + " | count: " + (peos != null ? peos.size() : 0));
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new ResourceNotFoundException("Programme Batch not found: " + programmeOrProgrammeBatchId);
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);

        List<PeoOutcome> existing = peoRepository.findByProgrammeBatchId(programmeBatchId);
        Map<String, PeoOutcome> existingByCode = existing.stream()
                .collect(Collectors.toMap(
                        p -> p.getCode().toLowerCase(),
                        p -> p,
                        (e1, e2) -> e1
                ));

        Set<String> processedIds = new HashSet<>();
        List<PeoOutcome> toSave = new ArrayList<>();

        if (peos != null) {
            for (PeoOutcome peo : peos) {
                peo.setProgrammeBatchId(programmeBatchId);

                String key = peo.getCode().toLowerCase();
                PeoOutcome targetPeo;
                if (existingByCode.containsKey(key)) {
                    targetPeo = existingByCode.get(key);
                    targetPeo.setStatement(peo.getStatement());
                } else {
                    targetPeo = peo;
                    if (targetPeo.getId() == null || targetPeo.getId().isBlank()) {
                        targetPeo.setId("peo-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                }

                processedIds.add(targetPeo.getId());
                toSave.add(targetPeo);
            }
        }

        List<PeoOutcome> toDelete = existing.stream()
                .filter(p -> !processedIds.contains(p.getId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            peoRepository.deleteAll(toDelete);
        }

        List<PeoOutcome> saved = peoRepository.saveAll(toSave);
        saved.sort(Comparator.comparing(PeoOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return saved;
    }

    private String resolveOfferingId(String offeringOrMasterCourseId) {
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

    @Transactional(readOnly = true)
    public List<CourseOutcome> getCOsByCourse(String masterCourseIdOrOfferingId) {
        System.out.println("[OutcomeService] getCOsByCourse called | masterCourseIdOrOfferingId: " + masterCourseIdOrOfferingId);
        if (masterCourseIdOrOfferingId != null && !masterCourseIdOrOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(masterCourseIdOrOfferingId);
        }
        String targetOfferingId = resolveOfferingId(masterCourseIdOrOfferingId);
        List<CourseOutcome> list = coRepository.findByProgrammeBatchCourseId(targetOfferingId);
        list.sort(Comparator.comparing(CourseOutcome::getCode, NATURAL_CODE_COMPARATOR));
        if (approvalRequestRepository != null && targetOfferingId != null) {
            approvalRequestRepository.findByProgrammeBatchCourseId(targetOfferingId).stream()
                    .filter(a -> a.getType() == ApprovalType.CO_DEFINITION || a.getType() == ApprovalType.CO_TARGETS || a.getType() == ApprovalType.COURSE_OUTCOMES_TARGETS)
                    .max(Comparator.comparing(ApprovalRequest::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .ifPresent(req -> {
                        for (CourseOutcome co : list) {
                            co.setRevisionReason(req.getRemarks());
                            co.setReviewedBy(req.getApprovedBy());
                        }
                    });
        }
        return list;
    }

    @Transactional
    public List<CourseOutcome> saveCOs(String masterCourseIdOrOfferingId, List<CourseOutcome> cos) {
        System.out.println("[OutcomeService] saveCOs called | masterCourseIdOrOfferingId: " + masterCourseIdOrOfferingId + " | count: " + (cos != null ? cos.size() : 0));
        if (masterCourseIdOrOfferingId != null && !masterCourseIdOrOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(masterCourseIdOrOfferingId);
            enforceCourseCoordinatorMutation(masterCourseIdOrOfferingId);
            enforceOfferingEditability(masterCourseIdOrOfferingId);
        }
        String targetOfferingId = resolveOfferingId(masterCourseIdOrOfferingId);
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.CO_DEFINITION, targetOfferingId, null);
        }
        List<CourseOutcome> existing = coRepository.findByProgrammeBatchCourseId(targetOfferingId);
        Map<String, CourseOutcome> existingByCode = existing.stream()
                .collect(Collectors.toMap(
                        c -> c.getCode().toLowerCase(),
                        c -> c,
                        (e1, e2) -> e1
                ));

        Set<String> processedIds = new HashSet<>();
        List<CourseOutcome> toSave = new ArrayList<>();

        if (cos != null) {
            for (CourseOutcome co : cos) {
                co.setProgrammeBatchCourseId(targetOfferingId);
                co.setStatus(ApprovalStatus.DRAFT);

                String key = co.getCode().toLowerCase();
                CourseOutcome targetCo;
                if (existingByCode.containsKey(key)) {
                    targetCo = existingByCode.get(key);
                    targetCo.setStatement(co.getStatement());
                    targetCo.setStatus(ApprovalStatus.DRAFT);
                    if (co.getTargetLevel() != null) {
                        targetCo.setTargetLevel(co.getTargetLevel());
                    }
                    if (co.getBloomsLevel() != null) {
                        targetCo.setBloomsLevel(co.getBloomsLevel());
                    }
                } else {
                    targetCo = co;
                    if (targetCo.getId() == null || targetCo.getId().isBlank()) {
                        targetCo.setId("co-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                    if (targetCo.getTargetLevel() == null) {
                        targetCo.setTargetLevel(new BigDecimal("2.50"));
                    }
                    if (targetCo.getBloomsLevel() == null) {
                        targetCo.setBloomsLevel("L3 - Apply");
                    }
                    targetCo.setStatus(ApprovalStatus.DRAFT);
                }

                processedIds.add(targetCo.getId());
                toSave.add(targetCo);
            }
        }

        List<CourseOutcome> toDelete = existing.stream()
                .filter(c -> !processedIds.contains(c.getId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            coRepository.deleteAll(toDelete);
        }

        List<CourseOutcome> saved = coRepository.saveAll(toSave);
        saved.sort(Comparator.comparing(CourseOutcome::getCode, NATURAL_CODE_COMPARATOR));
        return saved;
    }

    @Transactional
    public void deleteCourseOutcome(String masterCourseIdOrOfferingId, String coId) {
        System.out.println("[OutcomeService] deleteCourseOutcome called | masterCourseIdOrOfferingId: " + masterCourseIdOrOfferingId + " | coId: " + coId);
        if (masterCourseIdOrOfferingId != null && !masterCourseIdOrOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(masterCourseIdOrOfferingId);
            enforceCourseCoordinatorMutation(masterCourseIdOrOfferingId);
            enforceOfferingEditability(masterCourseIdOrOfferingId);
        }
        String targetOfferingId = resolveOfferingId(masterCourseIdOrOfferingId);
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.CO_DEFINITION, targetOfferingId, null);
        }
        CourseOutcome co = coRepository.findById(coId)
                .orElseThrow(() -> new ResourceNotFoundException("Course outcome not found: " + coId));
        if (targetOfferingId != null && !targetOfferingId.equalsIgnoreCase(co.getProgrammeBatchCourseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course outcome does not belong to the specified course offering.");
        }
        coPoMappingRepository.deleteByCourseOutcomeIdIn(List.of(co.getId()));
        coPsoMappingRepository.deleteByCourseOutcomeIdIn(List.of(co.getId()));
        coRepository.delete(co);
    }

    // --- Programme Target Benchmark Levels ---
    @Transactional(readOnly = true)
    public ProgrammeTargetDto getProgrammeTargets(String programmeOrProgrammeBatchId) {
        System.out.println("[OutcomeService] getProgrammeTargets called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String programmeBatchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            return ProgrammeTargetDto.builder()
                    .masterProgrammeId(programmeOrProgrammeBatchId)
                    .poTargets(Collections.emptyMap())
                    .psoTargets(Collections.emptyMap())
                    .build();
        }

        List<ProgrammeOutcome> pos = poRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        List<ProgrammeSpecificOutcome> psos = psoRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);

        Map<String, BigDecimal> poTargets = new LinkedHashMap<>();
        Map<String, BigDecimal> psoTargets = new LinkedHashMap<>();

        for (ProgrammeOutcome po : pos) {
            if (po.getCode() != null && po.getTarget() != null) {
                poTargets.put(po.getCode(), po.getTarget());
            }
        }
        for (ProgrammeSpecificOutcome pso : psos) {
            if (pso.getCode() != null && pso.getTarget() != null) {
                psoTargets.put(pso.getCode(), pso.getTarget());
            }
        }

        return ProgrammeTargetDto.builder()
                .masterProgrammeId(programmeOrProgrammeBatchId)
                .programmeBatchId(programmeBatchId)
                .poTargets(poTargets)
                .psoTargets(psoTargets)
                .build();
    }

    @Transactional(readOnly = true)
    public ProgrammeTargetDto getBatchProgrammeTargets(String programmeBatchId) {
        return getProgrammeTargets(programmeBatchId);
    }

    @Transactional
    public ProgrammeTargetDto saveProgrammeTargets(String programmeOrProgrammeBatchId, ProgrammeTargetDto dto) {
        System.out.println("[OutcomeService] saveProgrammeTargets called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        enforceProgrammeCoordinatorMutation(programmeOrProgrammeBatchId);
        if (approvalService != null && approvalService.isPoPsoTargetsApproved(programmeOrProgrammeBatchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Programme PO/PSO targets. A revision must be requested first.");
        }
        String programmeBatchId = (dto != null && dto.getProgrammeBatchId() != null && !dto.getProgrammeBatchId().isBlank())
                ? dto.getProgrammeBatchId()
                : resolveProgrammeBatchId(programmeOrProgrammeBatchId);

        if (programmeBatchId == null || programmeBatchId.isBlank()) {
            throw new ResourceNotFoundException("Programme Batch not found: " + programmeOrProgrammeBatchId);
        }
        batchLifecycleService.enforceBatchEditability(programmeBatchId);

        if (dto != null && dto.getPoTargets() != null && !dto.getPoTargets().isEmpty()) {
            List<ProgrammeOutcome> pos = new ArrayList<>(poRepository.findByProgrammeBatchId(programmeBatchId));
            Map<String, ProgrammeOutcome> poMap = pos.stream()
                    .filter(p -> p.getCode() != null)
                    .collect(Collectors.toMap(p -> p.getCode().trim().toUpperCase(), p -> p, (a, b) -> a));
            List<ProgrammeOutcome> toSave = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : dto.getPoTargets().entrySet()) {
                if (entry.getKey() == null) continue;
                String rawCode = entry.getKey().trim();
                String code = rawCode.toUpperCase();
                BigDecimal val = entry.getValue();

                ProgrammeOutcome po = poMap.get(code);
                if (po == null && !code.startsWith("PO") && !code.startsWith("PSO")) {
                    po = poMap.get("PO" + code);
                }

                if (po != null) {
                    po.setTarget(val);
                    toSave.add(po);
                } else {
                    String finalCode = rawCode.matches("\\d+") ? "PO" + rawCode : rawCode;
                    ProgrammeOutcome newPo = ProgrammeOutcome.builder()
                            .id("po-" + UUID.randomUUID().toString().substring(0, 8))
                            .programmeBatchId(programmeBatchId)
                            .code(finalCode)
                            .statement("Programme Outcome " + finalCode)
                            .target(val)
                            .build();
                    toSave.add(newPo);
                }
            }
            if (!toSave.isEmpty()) {
                poRepository.saveAll(toSave);
            }
        }

        if (dto != null && dto.getPsoTargets() != null && !dto.getPsoTargets().isEmpty()) {
            List<ProgrammeSpecificOutcome> psos = new ArrayList<>(psoRepository.findByProgrammeBatchId(programmeBatchId));
            Map<String, ProgrammeSpecificOutcome> psoMap = psos.stream()
                    .filter(p -> p.getCode() != null)
                    .collect(Collectors.toMap(p -> p.getCode().trim().toUpperCase(), p -> p, (a, b) -> a));
            List<ProgrammeSpecificOutcome> toSave = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : dto.getPsoTargets().entrySet()) {
                if (entry.getKey() == null) continue;
                String rawCode = entry.getKey().trim();
                String code = rawCode.toUpperCase();
                BigDecimal val = entry.getValue();

                ProgrammeSpecificOutcome pso = psoMap.get(code);
                if (pso == null && !code.startsWith("PSO")) {
                    pso = psoMap.get("PSO" + code);
                }

                if (pso != null) {
                    pso.setTarget(val);
                    toSave.add(pso);
                } else {
                    String finalCode = rawCode.matches("\\d+") ? "PSO" + rawCode : rawCode;
                    ProgrammeSpecificOutcome newPso = ProgrammeSpecificOutcome.builder()
                            .id("pso-" + UUID.randomUUID().toString().substring(0, 8))
                            .programmeBatchId(programmeBatchId)
                            .code(finalCode)
                            .statement("Programme Specific Outcome " + finalCode)
                            .target(val)
                            .build();
                    toSave.add(newPso);
                }
            }
            if (!toSave.isEmpty()) {
                psoRepository.saveAll(toSave);
            }
        }

        return getProgrammeTargets(programmeBatchId);
    }

    @Transactional
    public CourseMappingMatrixDto getCourseMappings(String masterCourseIdOrOfferingId) {
        System.out.println("[OutcomeService] getCourseMappings called | masterCourseIdOrOfferingId: " + masterCourseIdOrOfferingId);
        if (masterCourseIdOrOfferingId != null && !masterCourseIdOrOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(masterCourseIdOrOfferingId);
        }
        String targetOfferingId = resolveOfferingId(masterCourseIdOrOfferingId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(targetOfferingId).orElse(null);
        String masterCourseId = offering != null ? offering.getMasterCourseId() : targetOfferingId;
        String programmeBatchId = offering != null ? offering.getProgrammeBatchId() : null;

        MasterCourse course = masterCourseRepository.findById(masterCourseId).orElse(null);
        String progId = course != null ? course.getMasterProgrammeId() : null;

        List<CourseOutcome> cos = getCOsByCourse(targetOfferingId);
        List<ProgrammeOutcome> pos = (programmeBatchId != null) ? getPOsByProgramme(programmeBatchId) : ((progId != null) ? getPOsByProgramme(progId) : Collections.emptyList());
        List<ProgrammeSpecificOutcome> psos = (programmeBatchId != null) ? getPSOsByProgramme(programmeBatchId) : ((progId != null) ? getPSOsByProgramme(progId) : Collections.emptyList());

        List<String> coIds = cos.stream().map(CourseOutcome::getId).collect(Collectors.toList());

        List<CoPoMapping> poMappings = coIds.isEmpty() ? Collections.emptyList() : coPoMappingRepository.findByCourseOutcomeIdIn(coIds);
        List<CoPsoMapping> psoMappings = coIds.isEmpty() ? Collections.emptyList() : coPsoMappingRepository.findByCourseOutcomeIdIn(coIds);

        Map<String, Object> poKw = Collections.emptyMap();
        Map<String, Object> psoKw = Collections.emptyMap();

        Optional<CourseMappingKeyword> poKwOpt = courseMappingKeywordRepository.findByProgrammeBatchCourseIdAndKeywordType(targetOfferingId, "PO");
        if (poKwOpt.isPresent()) {
            try {
                poKw = objectMapper.readValue(poKwOpt.get().getKeywordsJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        } else if (coursePoKeywordsMap.containsKey(targetOfferingId)) {
            poKw = coursePoKeywordsMap.get(targetOfferingId);
        }

        Optional<CourseMappingKeyword> psoKwOpt = courseMappingKeywordRepository.findByProgrammeBatchCourseIdAndKeywordType(targetOfferingId, "PSO");
        if (psoKwOpt.isPresent()) {
            try {
                psoKw = objectMapper.readValue(psoKwOpt.get().getKeywordsJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        } else if (coursePsoKeywordsMap.containsKey(targetOfferingId)) {
            psoKw = coursePsoKeywordsMap.get(targetOfferingId);
        }

        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        Map<String, List<Integer>> poVals = new LinkedHashMap<>();
        Map<String, List<Integer>> psoVals = new LinkedHashMap<>();

        Map<String, String> coIdToCode = cos.stream().collect(Collectors.toMap(CourseOutcome::getId, CourseOutcome::getCode, (a, b) -> a));

        for (CourseOutcome co : cos) {
            matrix.put(co.getCode(), new LinkedHashMap<>());
        }

        for (CoPoMapping m : poMappings) {
            String coCode = coIdToCode.get(m.getCourseOutcomeId());
            if (coCode != null && matrix.containsKey(coCode)) {
                matrix.get(coCode).put(m.getPoCode(), m.getMappingLevel());
            }
            if (m.getMappingLevel() != null && m.getMappingLevel() > 0) {
                poVals.computeIfAbsent(m.getPoCode(), k -> new ArrayList<>()).add(m.getMappingLevel());
            }
        }

        for (CoPsoMapping m : psoMappings) {
            String coCode = coIdToCode.get(m.getCourseOutcomeId());
            if (coCode != null && matrix.containsKey(coCode)) {
                matrix.get(coCode).put(m.getPsoCode(), m.getMappingLevel());
            }
            if (m.getMappingLevel() != null && m.getMappingLevel() > 0) {
                psoVals.computeIfAbsent(m.getPsoCode(), k -> new ArrayList<>()).add(m.getMappingLevel());
            }
        }

        Map<String, BigDecimal> poAverages = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : poVals.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            poAverages.put(e.getKey(), BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        Map<String, BigDecimal> psoAverages = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : psoVals.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            psoAverages.put(e.getKey(), BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
        }

        return CourseMappingMatrixDto.builder()
                .masterCourseId(masterCourseId)
                .masterProgrammeId(progId)
                .cos(cos)
                .pos(pos)
                .psos(psos)
                .poMappings(poMappings)
                .psoMappings(psoMappings)
                .poKeywordsStore(poKw)
                .psoKeywordsStore(psoKw)
                .matrix(matrix)
                .poAverages(poAverages)
                .psoAverages(psoAverages)
                .build();
    }

    @Transactional
    public CourseMappingMatrixDto saveCourseMappings(String masterCourseIdOrOfferingId, CourseMappingMatrixDto dto) {
        System.out.println("[OutcomeService] saveCourseMappings called | masterCourseIdOrOfferingId: " + masterCourseIdOrOfferingId);
        if (masterCourseIdOrOfferingId != null && !masterCourseIdOrOfferingId.isBlank()) {
            enforceCourseOrOfferingScope(masterCourseIdOrOfferingId);
            enforceCourseCoordinatorMutation(masterCourseIdOrOfferingId);
            enforceOfferingEditability(masterCourseIdOrOfferingId);
        }
        String targetOfferingId = resolveOfferingId(masterCourseIdOrOfferingId);
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.CO_DEFINITION, targetOfferingId, null);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(targetOfferingId).orElse(null);
        String masterCourseId = offering != null ? offering.getMasterCourseId() : targetOfferingId;
        String programmeBatchId = offering != null ? offering.getProgrammeBatchId() : null;

        MasterCourse course = masterCourseRepository.findById(masterCourseId).orElse(null);
        String progId = course != null ? course.getMasterProgrammeId() : (dto != null && dto.getMasterProgrammeId() != null ? dto.getMasterProgrammeId() : null);

        Map<String, Object> poKwToReturn = Collections.emptyMap();
        Map<String, Object> psoKwToReturn = Collections.emptyMap();

        if (dto != null && dto.getPoKeywordsStore() != null) {
            poKwToReturn = dto.getPoKeywordsStore();
            coursePoKeywordsMap.put(targetOfferingId, poKwToReturn);
            try {
                String poJson = objectMapper.writeValueAsString(poKwToReturn);
                CourseMappingKeyword entity = courseMappingKeywordRepository.findByProgrammeBatchCourseIdAndKeywordType(targetOfferingId, "PO")
                        .orElse(CourseMappingKeyword.builder()
                                .id("kw-po-" + UUID.randomUUID().toString().substring(0, 8))
                                .programmeBatchCourseId(targetOfferingId)
                                .keywordType("PO")
                                .build());
                entity.setKeywordsJson(poJson);
                courseMappingKeywordRepository.save(entity);
            } catch (Exception ignored) {}
        }

        if (dto != null && dto.getPsoKeywordsStore() != null) {
            psoKwToReturn = dto.getPsoKeywordsStore();
            coursePsoKeywordsMap.put(targetOfferingId, psoKwToReturn);
            try {
                String psoJson = objectMapper.writeValueAsString(psoKwToReturn);
                CourseMappingKeyword entity = courseMappingKeywordRepository.findByProgrammeBatchCourseIdAndKeywordType(targetOfferingId, "PSO")
                        .orElse(CourseMappingKeyword.builder()
                                .id("kw-pso-" + UUID.randomUUID().toString().substring(0, 8))
                                .programmeBatchCourseId(targetOfferingId)
                                .keywordType("PSO")
                                .build());
                entity.setKeywordsJson(psoJson);
                courseMappingKeywordRepository.save(entity);
            } catch (Exception ignored) {}
        }

        List<CourseOutcome> cos = getCOsByCourse(targetOfferingId);
        List<String> coIds = cos.stream().map(CourseOutcome::getId).collect(Collectors.toList());

        List<CoPoMapping> existingPoMappings = coIds.isEmpty() ? Collections.emptyList() : coPoMappingRepository.findByCourseOutcomeIdIn(coIds);
        Map<String, CoPoMapping> existingPoMap = existingPoMappings.stream()
                .collect(Collectors.toMap(m -> m.getCourseOutcomeId() + "::" + m.getPoCode(), m -> m, (a, b) -> a));

        List<CoPsoMapping> existingPsoMappings = coIds.isEmpty() ? Collections.emptyList() : coPsoMappingRepository.findByCourseOutcomeIdIn(coIds);
        Map<String, CoPsoMapping> existingPsoMap = existingPsoMappings.stream()
                .collect(Collectors.toMap(m -> m.getCourseOutcomeId() + "::" + m.getPsoCode(), m -> m, (a, b) -> a));

        List<CoPoMapping> toSavePo = new ArrayList<>();
        Set<String> newPoKeys = new HashSet<>();
        if (dto != null && dto.getPoMappings() != null) {
            for (CoPoMapping m : dto.getPoMappings()) {
                if (m.getCourseOutcomeId() == null || m.getPoCode() == null) continue;
                String key = m.getCourseOutcomeId() + "::" + m.getPoCode();
                if (newPoKeys.contains(key)) continue;
                newPoKeys.add(key);

                CoPoMapping existing = existingPoMap.get(key);
                if (existing != null) {
                    existing.setMappingLevel(m.getMappingLevel() != null ? m.getMappingLevel() : 0);
                    toSavePo.add(existing);
                } else {
                    if (m.getId() == null || m.getId().isBlank()) {
                        m.setId("copomap-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                    toSavePo.add(m);
                }
            }
        }

        List<CoPoMapping> toDeletePo = existingPoMappings.stream()
                .filter(m -> !newPoKeys.contains(m.getCourseOutcomeId() + "::" + m.getPoCode()))
                .toList();
        if (!toDeletePo.isEmpty()) {
            coPoMappingRepository.deleteAllInBatch(toDeletePo);
        }
        if (!toSavePo.isEmpty()) {
            coPoMappingRepository.saveAll(toSavePo);
        }

        List<CoPsoMapping> toSavePso = new ArrayList<>();
        Set<String> newPsoKeys = new HashSet<>();
        if (dto != null && dto.getPsoMappings() != null) {
            for (CoPsoMapping m : dto.getPsoMappings()) {
                if (m.getCourseOutcomeId() == null || m.getPsoCode() == null) continue;
                String key = m.getCourseOutcomeId() + "::" + m.getPsoCode();
                if (newPsoKeys.contains(key)) continue;
                newPsoKeys.add(key);

                CoPsoMapping existing = existingPsoMap.get(key);
                if (existing != null) {
                    existing.setMappingLevel(m.getMappingLevel() != null ? m.getMappingLevel() : 0);
                    toSavePso.add(existing);
                } else {
                    if (m.getId() == null || m.getId().isBlank()) {
                        m.setId("copsomap-" + UUID.randomUUID().toString().substring(0, 8));
                    }
                    toSavePso.add(m);
                }
            }
        }

        List<CoPsoMapping> toDeletePso = existingPsoMappings.stream()
                .filter(m -> !newPsoKeys.contains(m.getCourseOutcomeId() + "::" + m.getPsoCode()))
                .toList();
        if (!toDeletePso.isEmpty()) {
            coPsoMappingRepository.deleteAllInBatch(toDeletePso);
        }
        if (!toSavePso.isEmpty()) {
            coPsoMappingRepository.saveAll(toSavePso);
        }

        return getCourseMappings(masterCourseIdOrOfferingId);
    }

    @Transactional(readOnly = true)
    public List<CourseOutcome> getOutcomesByOffering(String offeringId) {
        System.out.println("[OutcomeService] getOutcomesByOffering called | offeringId: " + offeringId);
        if (offeringId != null && !offeringId.isBlank()) {
            enforceOfferingScope(offeringId);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Offering not found: " + offeringId));
        return getCOsByCourse(offering.getId());
    }

    @Transactional
    public List<CourseOutcome> saveOutcomesByOffering(String offeringId, List<CourseOutcome> cos) {
        System.out.println("[OutcomeService] saveOutcomesByOffering called | offeringId: " + offeringId);
        if (offeringId != null && !offeringId.isBlank()) {
            enforceOfferingScope(offeringId);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Offering not found: " + offeringId));
        return saveCOs(offering.getId(), cos);
    }

    @Transactional
    public CourseMappingMatrixDto getMappingsByOffering(String offeringId) {
        System.out.println("[OutcomeService] getMappingsByOffering called | offeringId: " + offeringId);
        if (offeringId != null && !offeringId.isBlank()) {
            enforceOfferingScope(offeringId);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Offering not found: " + offeringId));
        return getCourseMappings(offering.getId());
    }

    @Transactional
    public CourseMappingMatrixDto saveMappingsByOffering(String offeringId, CourseMappingMatrixDto dto) {
        System.out.println("[OutcomeService] saveMappingsByOffering called | offeringId: " + offeringId);
        if (offeringId != null && !offeringId.isBlank()) {
            enforceOfferingScope(offeringId);
        }
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Offering not found: " + offeringId));
        return saveCourseMappings(offering.getId(), dto);
    }

    // --- Atomic Programme Batch Outcome Bundle (POs, PSOs, PEOs, Targets in single transaction) ---

    @Transactional(readOnly = true)
    public com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto getProgrammeBatchOutcomeBundle(String programmeOrProgrammeBatchId) {
        System.out.println("[OutcomeService] getProgrammeBatchOutcomeBundle called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        String batchId = resolveProgrammeBatchId(programmeOrProgrammeBatchId);
        ProgrammeBatch batch = (batchId != null) ? programmeBatchRepository.findById(batchId).orElse(null) : null;
        String masterProgId = (batch != null) ? batch.getMasterProgrammeId() : programmeOrProgrammeBatchId;

        List<ProgrammeOutcome> pos = getPOsByProgramme(programmeOrProgrammeBatchId);
        List<ProgrammeSpecificOutcome> psos = getPSOsByProgramme(programmeOrProgrammeBatchId);
        List<PeoOutcome> peos = getPEOsByProgramme(programmeOrProgrammeBatchId);
        ProgrammeTargetDto targets = getProgrammeTargets(programmeOrProgrammeBatchId);

        return com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto.builder()
                .programmeBatchId(batchId)
                .masterProgrammeId(masterProgId)
                .pos(pos)
                .psos(psos)
                .peos(peos)
                .poTargets(targets != null ? targets.getPoTargets() : null)
                .psoTargets(targets != null ? targets.getPsoTargets() : null)
                .build();
    }

    @Transactional
    public com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto saveProgrammeBatchOutcomeBundle(String programmeOrProgrammeBatchId, com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto bundle) {
        System.out.println("[OutcomeService] saveProgrammeBatchOutcomeBundle called | programmeOrProgrammeBatchId: " + programmeOrProgrammeBatchId);
        enforceBatchOrProgrammeScope(programmeOrProgrammeBatchId);
        enforceProgrammeCoordinatorMutation(programmeOrProgrammeBatchId);
        String batchId = (bundle != null && bundle.getProgrammeBatchId() != null && !bundle.getProgrammeBatchId().isBlank())
                ? bundle.getProgrammeBatchId()
                : resolveProgrammeBatchId(programmeOrProgrammeBatchId);

        if (batchId == null || batchId.isBlank()) {
            throw new ResourceNotFoundException("Programme Batch not found: " + programmeOrProgrammeBatchId);
        }
        batchLifecycleService.enforceBatchEditability(batchId);

        if (bundle == null) {
            return getProgrammeBatchOutcomeBundle(programmeOrProgrammeBatchId);
        }

        // 1. Save POs (with competencies and targets if provided)
        List<ProgrammeOutcome> savedPos = null;
        if (bundle.getPos() != null) {
            savedPos = savePOs(batchId, bundle.getPos());
        } else {
            savedPos = getPOsByProgramme(batchId);
        }

        // 2. Save PSOs (with competencies and targets if provided)
        List<ProgrammeSpecificOutcome> savedPsos = null;
        if (bundle.getPsos() != null) {
            savedPsos = savePSOs(batchId, bundle.getPsos());
        } else {
            savedPsos = getPSOsByProgramme(batchId);
        }

        // 3. Save PEOs
        List<PeoOutcome> savedPeos = null;
        if (bundle.getPeos() != null) {
            savedPeos = savePEOs(batchId, bundle.getPeos());
        } else {
            savedPeos = getPEOsByProgramme(batchId);
        }

        // 4. Save Targets if provided in the bundle
        ProgrammeTargetDto targetDto = null;
        if (bundle.getPoTargets() != null || bundle.getPsoTargets() != null) {
            ProgrammeTargetDto tDto = ProgrammeTargetDto.builder()
                    .programmeBatchId(batchId)
                    .poTargets(bundle.getPoTargets())
                    .psoTargets(bundle.getPsoTargets())
                    .build();
            targetDto = saveProgrammeTargets(batchId, tDto);
        } else {
            targetDto = getProgrammeTargets(batchId);
        }

        ProgrammeBatch batch = programmeBatchRepository.findById(batchId).orElse(null);
        String masterProgId = (batch != null) ? batch.getMasterProgrammeId() : (bundle.getMasterProgrammeId() != null ? bundle.getMasterProgrammeId() : programmeOrProgrammeBatchId);

        return com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto.builder()
                .programmeBatchId(batchId)
                .masterProgrammeId(masterProgId)
                .pos(savedPos)
                .psos(savedPsos)
                .peos(savedPeos)
                .poTargets(targetDto != null ? targetDto.getPoTargets() : null)
                .psoTargets(targetDto != null ? targetDto.getPsoTargets() : null)
                .build();
    }
}
