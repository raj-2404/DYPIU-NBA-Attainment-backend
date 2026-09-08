package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.ReportAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AcademicService academicService;
    private final ReportAccessService reportAccessService;
    private final CurrentUserScopeService currentUserScopeService;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final CourseAtrRepository courseAtrRepository;
    private final UserRepository userRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final AttainmentConfigurationRepository configRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final CoPoMappingRepository coPoMappingRepository;
    private final CoPsoMappingRepository coPsoMappingRepository;
    private final StudentCoMarkRepository studentCoMarkRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final ProgrammeAtrRepository programmeAtrRepository;
    private final com.dypiu.nba.security.RequestScopeAuthorizer requestScopeAuthorizer;

    @GetMapping("/director")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDirectorDashboard(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String directorEmail,
            Principal principal) {
        CurrentUserScope scope = currentUserScopeService.getCurrentUserScope(principal);
        String targetSchoolId = null;

        if (scope.isDirector()) {
            targetSchoolId = scope.getRequiredSchoolId();
        } else if (scope.isIqac()) {
            requestScopeAuthorizer.assertRequestedDirectorEmail(directorEmail);
            requestScopeAuthorizer.assertRequestedSchool(schoolId);
            if (schoolId != null && !schoolId.isBlank()) {
                targetSchoolId = schoolId.trim();
            } else if (directorEmail != null && !directorEmail.isBlank()) {
                targetSchoolId = schoolRepository.findByDirectorEmailIgnoreCase(directorEmail.trim())
                        .map(School::getId).orElse(null);
            }
            if (targetSchoolId == null) {
                targetSchoolId = scope.getSchoolId();
            }
        } else if (scope.getSchoolId() != null) {
            targetSchoolId = scope.getSchoolId();
        }

        if (targetSchoolId == null || targetSchoolId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School scope cannot be determined for Director dashboard.");
        }

        final String finalSchoolId = targetSchoolId;
        School school = schoolRepository.findById(finalSchoolId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found: " + finalSchoolId));

        List<Department> depts = departmentRepository.findBySchoolId(finalSchoolId);
        List<String> deptIds = depts.stream().map(Department::getId).toList();
        List<MasterProgramme> progs = deptIds.isEmpty() ? Collections.emptyList() : masterProgrammeRepository.findByDepartmentIdIn(deptIds);
        List<String> progIds = progs.stream().map(MasterProgramme::getId).toList();
        List<ProgrammeBatch> activeBatches = progIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(progIds).stream()
                .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());

        String targetEmail = school.getDirectorEmail() != null && !school.getDirectorEmail().isBlank()
                ? school.getDirectorEmail()
                : (scope.getEmail() != null ? scope.getEmail() : directorEmail);
        DirectorSetupProgressDto progress = academicService.getDirectorSetupProgress(finalSchoolId, targetEmail);

        long assignedHodCount = depts.stream()
                .filter(d -> (d.getHod() != null && !d.getHod().isBlank() && !d.getHod().equalsIgnoreCase("Unassigned"))
                        || (d.getHodEmail() != null && !d.getHodEmail().isBlank() && !d.getHodEmail().equalsIgnoreCase("Unassigned")))
                .count();
        long unassignedHodCount = depts.size() - assignedHodCount;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("departments", depts.size());
        stats.put("departmentsCount", depts.size());
        stats.put("assignedHODs", assignedHodCount);
        stats.put("assignedHODsCount", assignedHodCount);
        stats.put("assignedHods", assignedHodCount);
        stats.put("assignedHodsCount", assignedHodCount);
        stats.put("unassignedHODs", unassignedHodCount);
        stats.put("unassignedHODsCount", unassignedHodCount);
        stats.put("unassignedHods", unassignedHodCount);
        stats.put("unassignedHodsCount", unassignedHodCount);
        stats.put("programmes", progs.size());
        stats.put("programmesCount", progs.size());
        stats.put("activeBatches", activeBatches.size());
        stats.put("activeBatchesCount", activeBatches.size());

        Map<String, Boolean> workflowProgress = new LinkedHashMap<>();
        workflowProgress.put("1", !depts.isEmpty());
        workflowProgress.put("2", !progs.isEmpty());
        workflowProgress.put("3", !activeBatches.isEmpty());
        workflowProgress.put("4", progress != null && progress.getOverallStatus() == SetupStepStatus.COMPLETED);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("school", school);
        data.put("setupProgress", progress);
        data.put("workflowProgress", workflowProgress);
        data.put("statistics", stats);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true).data(data).build());
    }

    @GetMapping("/hod")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHodDashboard(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hodEmail,
            Principal principal) {
        CurrentUserScope scope = currentUserScopeService.getCurrentUserScope(principal);
        String targetSchoolId = null;
        String targetDeptId = null;

        if (scope.isHod()) {
            targetSchoolId = scope.getRequiredSchoolId();
            targetDeptId = scope.getRequiredDepartmentId();
        } else if (scope.isIqac()) {
            requestScopeAuthorizer.assertRequestedHodEmail(hodEmail);
            requestScopeAuthorizer.assertRequestedDepartment(departmentId);
            if (departmentId != null && !departmentId.isBlank()) {
                targetDeptId = departmentId.trim();
            } else if (hodEmail != null && !hodEmail.isBlank()) {
                List<Department> depts = departmentRepository.findByHodEmailIgnoreCase(hodEmail.trim());
                if (!depts.isEmpty()) targetDeptId = depts.get(0).getId();
            }
            if (targetDeptId == null) {
                targetDeptId = scope.getDepartmentId();
            }
            targetSchoolId = scope.getSchoolId();
        } else {
            targetSchoolId = scope.getSchoolId();
            targetDeptId = scope.getDepartmentId();
            if (targetDeptId == null && departmentId != null && !departmentId.isBlank()) {
                targetDeptId = departmentId.trim();
            }
        }

        if (targetDeptId == null || targetDeptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department scope cannot be determined for HOD dashboard.");
        }

        final String finalDeptId = targetDeptId;
        Department primaryDept = departmentRepository.findById(finalDeptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found: " + finalDeptId));

        if (targetSchoolId != null && primaryDept.getSchoolId() != null && !primaryDept.getSchoolId().equals(targetSchoolId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Department does not belong to your school scope.");
        }

        List<Department> matchedDepts = List.of(primaryDept);
        List<MasterProgramme> progs = masterProgrammeRepository.findByDepartmentId(finalDeptId);
        Set<String> progIds = progs.stream().map(MasterProgramme::getId).collect(Collectors.toSet());
        List<ProgrammeBatch> activeBatches = progIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(progIds).stream()
                .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());
        Set<String> programmeBatchIds = activeBatches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
        List<ProgrammeBatchCourse> offerings = programmeBatchIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdInAndDeletedAtIsNull(programmeBatchIds);
        List<ProgrammeBatchCourse> courses = offerings;

        long allocationsPending = progIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.COURSE_ALLOCATION || a.getType() == ApprovalType.COURSE_OFFERING)
                        && a.getMasterProgrammeId() != null && progIds.contains(a.getMasterProgrammeId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long targetsPending = progIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.PO_PSO_TARGETS
                        && a.getMasterProgrammeId() != null && progIds.contains(a.getMasterProgrammeId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long programmeAtrPending = progIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.PROGRAMME_ATR
                        && a.getMasterProgrammeId() != null && progIds.contains(a.getMasterProgrammeId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long pendingApprovalsCount = allocationsPending + targetsPending + programmeAtrPending;

        Map<String, Object> pendingBreakdown = new LinkedHashMap<>();
        pendingBreakdown.put("allocationsPending", allocationsPending);
        pendingBreakdown.put("targetsPending", targetsPending);
        pendingBreakdown.put("programmeAtrPending", programmeAtrPending);

        String targetEmail = primaryDept.getHodEmail() != null && !primaryDept.getHodEmail().isBlank()
                ? primaryDept.getHodEmail()
                : (scope.getEmail() != null ? scope.getEmail() : hodEmail);
        HodSetupProgressDto progress = academicService.getHodSetupProgress(finalDeptId, targetEmail);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("programmes", progs.size());
        stats.put("programmesCount", progs.size());
        stats.put("coursesCount", courses.size());
        stats.put("activeBatches", activeBatches.size());
        stats.put("activeBatchesCount", activeBatches.size());
        stats.put("courseOfferings", offerings.size());
        stats.put("pendingApprovalsCount", pendingApprovalsCount);
        stats.put("pendingBreakdown", pendingBreakdown);

        ProgrammeBatch activeProgrammeBatch = activeBatches.isEmpty() ? null : activeBatches.get(0);

        Map<String, Boolean> workflowProgress = new LinkedHashMap<>();
        workflowProgress.put("1", !progs.isEmpty());
        workflowProgress.put("2", !offerings.isEmpty());
        workflowProgress.put("3", !activeBatches.isEmpty());
        workflowProgress.put("4", progress != null && progress.getOverallStatus() == SetupStepStatus.COMPLETED);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("department", primaryDept);
        data.put("departments", matchedDepts);
        data.put("setupProgress", progress);
        data.put("activeBatch", activeProgrammeBatch != null ? activeProgrammeBatch.getName() : "");
        data.put("workflowProgress", workflowProgress);
        data.put("statistics", stats);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true).data(data).build());
    }

    

    @GetMapping("/programme-coordinator")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProgrammeCoordinatorDashboard(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String coordinatorEmail,
            Principal principal) {
        requestScopeAuthorizer.assertRequestedCoordinatorEmail(coordinatorEmail);
        requestScopeAuthorizer.assertRequestedProgramme(masterProgrammeId);
        String effectiveProgId = (masterProgrammeId != null && !masterProgrammeId.isBlank()) ? masterProgrammeId : masterProgrammeId;
        CurrentUserScope scope = currentUserScopeService.getCurrentUserScope(principal);
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail.trim().toLowerCase()
                : (scope != null && scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null);

        String targetProgId = null;

        if (scope.isProgrammeCoordinator()) {
            if (effectiveProgId != null && !effectiveProgId.isBlank()) {
                boolean matchesDirect = scope.getMasterProgrammeId() != null && effectiveProgId.trim().equals(scope.getMasterProgrammeId().trim());
                boolean matchesBatch = false;
                if (!matchesDirect && effectiveEmail != null && !effectiveEmail.isBlank()) {
                    List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
                    matchesBatch = batches.stream().anyMatch(b -> effectiveProgId.trim().equals(b.getMasterProgrammeId()));
                }
                if (!matchesDirect && !matchesBatch && scope.isIqac()) {
                    // allow
                } else if (!matchesDirect && !matchesBatch) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: MasterProgramme is outside your assigned scope.");
                }
                targetProgId = effectiveProgId.trim();
            } else if (effectiveEmail != null && !effectiveEmail.isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
                if (batches != null && !batches.isEmpty()) {
                    targetProgId = batches.get(0).getMasterProgrammeId();
                }
            } else if (scope.getMasterProgrammeId() != null && !scope.getMasterProgrammeId().isBlank()) {
                targetProgId = scope.getMasterProgrammeId().trim();
            }
        } else if (scope.isIqac()) {
            if (effectiveProgId != null && !effectiveProgId.isBlank()) {
                targetProgId = effectiveProgId.trim();
            } else if (effectiveEmail != null && !effectiveEmail.isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
                if (batches != null && !batches.isEmpty()) {
                    targetProgId = batches.get(0).getMasterProgrammeId();
                }
            } else if (scope.getMasterProgrammeId() != null) {
                targetProgId = scope.getMasterProgrammeId();
            }
        } else if (scope.isHod()) {
            if (effectiveProgId != null && !effectiveProgId.isBlank()) {
                targetProgId = effectiveProgId.trim();
            }
        } else if (scope.isDirector()) {
            if (effectiveProgId != null && !effectiveProgId.isBlank()) {
                targetProgId = effectiveProgId.trim();
            }
        } else if (scope.getMasterProgrammeId() != null) {
            targetProgId = scope.getMasterProgrammeId();
        } else if (effectiveProgId != null && !effectiveProgId.isBlank()) {
            targetProgId = effectiveProgId.trim();
        }

        if (targetProgId == null || targetProgId.isBlank()) {
            if (effectiveEmail != null && !effectiveEmail.isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
                if (batches != null && !batches.isEmpty()) {
                    targetProgId = batches.get(0).getMasterProgrammeId();
                }
            }
        }

        if (targetProgId == null || targetProgId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MasterProgramme scope cannot be determined for MasterProgramme Coordinator dashboard.");
        }

        final String finalProgId = targetProgId;
        MasterProgramme prog = masterProgrammeRepository.findById(finalProgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MasterProgramme not found: " + finalProgId));

        if (scope.isProgrammeCoordinator()) {
            if (scope.hasDepartmentScope() && prog.getDepartmentId() != null && !prog.getDepartmentId().equals(scope.getDepartmentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: MasterProgramme does not belong to your assigned department.");
            }
            if (scope.hasSchoolScope() && prog.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
                if (dept != null && dept.getSchoolId() != null && !dept.getSchoolId().equals(scope.getRequiredSchoolId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: MasterProgramme does not belong to your assigned school.");
                }
            }
        } else if (scope.isHod()) {
            if (prog.getDepartmentId() != null && !prog.getDepartmentId().equals(scope.getRequiredDepartmentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: MasterProgramme does not belong to your assigned department.");
            }
        } else if (scope.isDirector()) {
            if (prog.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
                if (dept != null && dept.getSchoolId() != null && !dept.getSchoolId().equals(scope.getRequiredSchoolId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: MasterProgramme does not belong to your assigned school.");
                }
            }
        }

        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(finalProgId);
        Set<String> programmeBatchIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
        List<ProgrammeBatchCourse> offerings = programmeBatchIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdInAndDeletedAtIsNull(programmeBatchIds);
        List<ProgrammeBatchCourse> courses = offerings;

        List<String> offeringIds = offerings.stream().map(ProgrammeBatchCourse::getId).collect(Collectors.toList());
        long configPending = offeringIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.ATTAINMENT_CONFIGURATION || a.getType() == ApprovalType.ATTAINMENT_SETTINGS)
                        && a.getProgrammeBatchCourseId() != null && offeringIds.contains(a.getProgrammeBatchCourseId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long coTargetsPending = offeringIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.CO_DEFINITION || a.getType() == ApprovalType.CO_TARGETS || a.getType() == ApprovalType.COURSE_OUTCOMES_TARGETS)
                        && a.getProgrammeBatchCourseId() != null && offeringIds.contains(a.getProgrammeBatchCourseId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long courseAtrPending = offeringIds.isEmpty() ? 0 : approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.COURSE_ATR
                        && a.getProgrammeBatchCourseId() != null && offeringIds.contains(a.getProgrammeBatchCourseId())
                        && a.getStatus() == ApprovalStatus.PENDING)
                .count();
        long pendingVerifications = configPending + coTargetsPending + courseAtrPending;

        Map<String, Object> pendingBreakdown = new LinkedHashMap<>();
        pendingBreakdown.put("configPending", configPending);
        pendingBreakdown.put("coTargetsPending", coTargetsPending);
        pendingBreakdown.put("courseAtrPending", courseAtrPending);

        String targetEmail = prog.getCoordinatorEmail() != null && !prog.getCoordinatorEmail().isBlank()
                ? prog.getCoordinatorEmail()
                : (scope.getEmail() != null ? scope.getEmail() : null);
        ProgrammeCoordinatorSetupProgressDto progress = academicService.getProgrammeCoordinatorSetupProgress(targetEmail, finalProgId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("programmeBatches", batches.size());
        stats.put("programmeBatchesCount", batches.size());
        stats.put("totalProgrammeBatches", batches.size());
        stats.put("courses", courses.size());
        stats.put("coursesCount", courses.size());
        stats.put("programmeBatchCourses", offerings.size());
        stats.put("programmeBatchCoursesCount", offerings.size());
        stats.put("totalProgrammeBatchCourses", offerings.size());
        stats.put("courseOfferings", offerings.size());
        stats.put("pendingCourseAtrApprovals", courseAtrPending);
        stats.put("pendingVerifications", pendingVerifications);
        stats.put("pendingBreakdown", pendingBreakdown);

        ProgrammeBatch activeProgrammeBatch = batches.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).findFirst().orElse(batches.isEmpty() ? null : batches.get(0));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("masterProgrammeId", prog.getId());
        data.put("programme", prog);
        data.put("setupProgress", progress);
        data.put("batches", batches);
        data.put("activeBatch", activeProgrammeBatch != null ? activeProgrammeBatch.getName() : "");
        data.put("statistics", stats);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true).data(data).build());
    }

    

    @GetMapping({"/course-coordinator", "/faculty"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCourseCoordinatorDashboard(
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            Principal principal) {
        String effectiveOfferingOrMasterCourseId = (programmeBatchCourseId != null && !programmeBatchCourseId.isBlank())
                ? programmeBatchCourseId
                : ((masterCourseId != null && !masterCourseId.isBlank()) ? masterCourseId : masterCourseId);
        User user = reportAccessService.getAuthenticatedUser(principal);
        CurrentUserScope scope = currentUserScopeService.getCurrentUserScope(principal);

        List<ProgrammeBatchCourse> allOfferings = programmeBatchCourseRepository.findAll();
        List<ProgrammeBatchCourse> assignedOfferings = allOfferings.stream()
                .filter(o -> user != null && o.getCourseCoordinatorId() != null && java.util.Objects.equals(o.getCourseCoordinatorId(), user.getId()))
                .filter(o -> scope == null || !scope.isFaculty() || academicService.isCourseAllocationApproved(o))
                .collect(Collectors.toList());

        ProgrammeBatchCourse targetOffering = null;
        if (effectiveOfferingOrMasterCourseId != null && !effectiveOfferingOrMasterCourseId.isBlank()) {
            if (scope != null && scope.isFaculty()) {
                boolean assignedToMasterCourse = assignedOfferings.stream().anyMatch(o -> 
                        (o.getMasterCourseId() != null && o.getMasterCourseId().equalsIgnoreCase(effectiveOfferingOrMasterCourseId.trim())) || 
                        (o.getId() != null && o.getId().equalsIgnoreCase(effectiveOfferingOrMasterCourseId.trim())));
                if (!assignedToMasterCourse) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this MasterCourse / MasterCourse Offering or allocation is not approved yet.");
                }
            }
            targetOffering = programmeBatchCourseRepository.findByMasterCourseId(effectiveOfferingOrMasterCourseId.trim()).stream()
                .filter(o -> scope == null || !scope.isFaculty() || assignedOfferings.contains(o))
                .findFirst()
                .orElse(null);
            if (targetOffering == null) {
                targetOffering = programmeBatchCourseRepository.findById(effectiveOfferingOrMasterCourseId.trim()).orElse(null);
            }
        }
        if (targetOffering == null && !assignedOfferings.isEmpty()) {
            targetOffering = assignedOfferings.get(0);
        }

        ProgrammeBatchCourse course = targetOffering;
        String offeringId = targetOffering != null ? targetOffering.getId() : null;

        List<CourseOutcome> cos = (offeringId != null) ? courseOutcomeRepository.findByProgrammeBatchCourseId(offeringId) : Collections.emptyList();
        List<String> coIds = cos.stream().map(CourseOutcome::getId).toList();

        boolean outcomesDone = !cos.isEmpty();
        boolean targetsDone = outcomesDone && cos.stream().allMatch(c -> c.getTargetLevel() != null);
        boolean mappingDone = !coIds.isEmpty() && (!coPoMappingRepository.findByCourseOutcomeIdIn(coIds).isEmpty() || !coPsoMappingRepository.findByCourseOutcomeIdIn(coIds).isEmpty());
        boolean configDone = (offeringId != null) && configRepository.findByProgrammeBatchCourseId(offeringId).isPresent();
        boolean marksDone = (offeringId != null) && (!studentCoMarkRepository.findByProgrammeBatchCourseId(offeringId).isEmpty() || !uploadedDocumentRepository.findByProgrammeBatchCourseId(offeringId).isEmpty());
        boolean atrDone = (offeringId != null) && !courseAtrRepository.findByProgrammeBatchCourseId(offeringId).isEmpty();

        Map<String, Boolean> workflowProgress = new LinkedHashMap<>();
        workflowProgress.put("/outcomes", outcomesDone);
        workflowProgress.put("/co-targets", targetsDone);
        workflowProgress.put("/co-mapping", mappingDone);
        workflowProgress.put("/attainment-config", configDone);
        workflowProgress.put("/marks-upload", marksDone);
        workflowProgress.put("/course-atr", atrDone);

        boolean isConfigRevision = (offeringId != null) && approvalRequestRepository.findAll().stream()
                .anyMatch(a -> a.getType() == ApprovalType.ATTAINMENT_CONFIGURATION && offeringId.equalsIgnoreCase(a.getProgrammeBatchCourseId()) && a.getStatus() == ApprovalStatus.REVISION_REQUESTED);
        boolean isCoRevision = (offeringId != null) && approvalRequestRepository.findAll().stream()
                .anyMatch(a -> (a.getType() == ApprovalType.CO_DEFINITION || a.getType() == ApprovalType.CO_TARGETS) && offeringId.equalsIgnoreCase(a.getProgrammeBatchCourseId()) && a.getStatus() == ApprovalStatus.REVISION_REQUESTED);
        boolean isAtrRevision = (offeringId != null) && courseAtrRepository.findByProgrammeBatchCourseId(offeringId).stream()
                .anyMatch(a -> a.getStatus() == CourseAtrStatus.REVISION_REQUESTED);
        boolean hasRevision = isConfigRevision || isCoRevision || isAtrRevision;

        Map<String, Boolean> revisions = new LinkedHashMap<>();
        revisions.put("hasRevision", hasRevision);
        revisions.put("isConfigRevision", isConfigRevision);
        revisions.put("isCoRevision", isCoRevision);
        revisions.put("isAtrRevision", isAtrRevision);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assignedProgrammeBatchCoursesCount", assignedOfferings.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("masterCourseId", course != null ? course.getId() : (effectiveOfferingOrMasterCourseId != null ? effectiveOfferingOrMasterCourseId.trim() : null));
        data.put("programmeBatchCourseId", offeringId);
        data.put("programmeBatchId", targetOffering != null ? targetOffering.getProgrammeBatchId() : programmeBatchId);
        data.put("course", course);
        data.put("workflowProgress", workflowProgress);
        data.put("revisions", revisions);
        data.put("assignedProgrammeBatchCourses", assignedOfferings);
        data.put("statistics", stats);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder().success(true).data(data).build());
    }
}
