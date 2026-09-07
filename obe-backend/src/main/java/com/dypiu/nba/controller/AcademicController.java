package com.dypiu.nba.controller;

import com.dypiu.nba.dto.DirectorSetupProgressDto;
import com.dypiu.nba.dto.DirectorSchoolSummaryDto;
import com.dypiu.nba.dto.DepartmentSummaryDto;
import com.dypiu.nba.dto.HodDepartmentSummaryDto;
import com.dypiu.nba.dto.HodSetupProgressDto;
import com.dypiu.nba.dto.ProgrammeCoordinatorSummaryDto;
import com.dypiu.nba.dto.ProgrammeCoordinatorSetupProgressDto;
import com.dypiu.nba.dto.CourseCoordinatorSummaryDto;
import com.dypiu.nba.dto.CourseCoordinatorSetupProgressDto;
import com.dypiu.nba.dto.UserDto;
import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.MappingService;
import com.dypiu.nba.service.BatchLifecycleService;
import com.dypiu.nba.security.RequestScopeAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/academic", "/api/v1/academic"})
@RequiredArgsConstructor
public class AcademicController {

    private final AcademicService academicService;
    private final MappingService mappingService;
    private final BatchLifecycleService batchLifecycleService;
    private final com.dypiu.nba.service.OutcomeService outcomeService;
    private final com.dypiu.nba.service.AtrService atrService;
    private final RequestScopeAuthorizer requestScopeAuthorizer;
    private final com.dypiu.nba.repository.ProgrammeBatchCourseRepository programmeBatchCourseRepository;


    // --- Users by Role ---
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByRole(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(ApiResponse.<List<UserDto>>builder()
                .success(true)
                .data(academicService.getUsersByRole(role, departmentId))
                .build());
    }

    @GetMapping({"/faculty", "/course-coordinators"})
    public ResponseEntity<ApiResponse<List<UserDto>>> getFaculty() {
        return ResponseEntity.ok(ApiResponse.<List<UserDto>>builder()
                .success(true)
                .data(academicService.getUsersByRole("FACULTY"))
                .build());
    }

    @GetMapping("/programme-coordinators")
    public ResponseEntity<ApiResponse<List<UserDto>>> getProgrammeCoordinators() {
        return ResponseEntity.ok(ApiResponse.<List<UserDto>>builder()
                .success(true)
                .data(academicService.getUsersByRole("PROGRAMME_COORDINATOR"))
                .build());
    }

    @GetMapping("/hods")
    public ResponseEntity<ApiResponse<List<UserDto>>> getHods() {
        return ResponseEntity.ok(ApiResponse.<List<UserDto>>builder()
                .success(true)
                .data(academicService.getUsersByRole("HOD"))
                .build());
    }

    @GetMapping("/directors")
    public ResponseEntity<ApiResponse<List<UserDto>>> getDirectors() {
        return ResponseEntity.ok(ApiResponse.<List<UserDto>>builder()
                .success(true)
                .data(academicService.getUsersByRole("DIRECTOR"))
                .build());
    }

    // --- Director School Summary ---
    @GetMapping("/director/school-summary")
    public ResponseEntity<ApiResponse<DirectorSchoolSummaryDto>> getDirectorSchoolSummary(
            @RequestParam(required = false) String directorEmail,
            java.security.Principal principal) {
        String email = (directorEmail != null && !directorEmail.isBlank())
                ? directorEmail
                : (principal != null ? principal.getName() : null);
        return ResponseEntity.ok(ApiResponse.<DirectorSchoolSummaryDto>builder()
                .success(true)
                .data(academicService.getDirectorSchoolSummary(email))
                .build());
    }

    // --- Director Department Summary ---
    @GetMapping("/director/department-summary")
    public ResponseEntity<ApiResponse<List<DepartmentSummaryDto>>> getDepartmentSummary(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String directorEmail,
            java.security.Principal principal) {
        String email = (directorEmail != null && !directorEmail.isBlank())
                ? directorEmail
                : (principal != null ? principal.getName() : null);
        return ResponseEntity.ok(ApiResponse.<List<DepartmentSummaryDto>>builder()
                .success(true)
                .data(academicService.getDepartmentSummary(schoolId, email))
                .build());
    }

    // --- Director Setup Progress ---
    @GetMapping("/director/setup-progress")
    public ResponseEntity<ApiResponse<DirectorSetupProgressDto>> getDirectorSetupProgress(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String directorEmail,
            @RequestParam(required = false) String id,
            java.security.Principal principal) {
        String targetSchool = (schoolId != null && !schoolId.isBlank()) ? schoolId : id;
        String email = (directorEmail != null && !directorEmail.isBlank())
                ? directorEmail
                : (principal != null ? principal.getName() : null);
        return ResponseEntity.ok(ApiResponse.<DirectorSetupProgressDto>builder()
                .success(true)
                .data(academicService.getDirectorSetupProgress(targetSchool, email))
                .build());
    }

    @RequestMapping(value = "/director/setup-progress", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<DirectorSetupProgressDto>> updateDirectorSetupProgress(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) Integer step,
            @RequestParam(required = false) Integer currentStep,
            @RequestParam(required = false) String completedStep,
            @RequestBody(required = false) java.util.Map<String, Object> body,
            java.security.Principal principal) {
        String targetSchool = schoolId != null ? schoolId : id;
        Integer targetStep = step != null ? step : currentStep;
        String finalCompletedStep = completedStep;
        java.util.List<String> completedStepsList = null;

        if (body != null) {
            if (targetSchool == null && body.containsKey("identifier")) targetSchool = String.valueOf(body.get("identifier"));
            if (targetSchool == null && body.containsKey("schoolId")) targetSchool = String.valueOf(body.get("schoolId"));
            if (targetStep == null && body.containsKey("step")) {
                try { targetStep = Integer.parseInt(String.valueOf(body.get("step"))); } catch (Exception ignored) {}
            }
            if (targetStep == null && body.containsKey("currentStep")) {
                try { targetStep = Integer.parseInt(String.valueOf(body.get("currentStep"))); } catch (Exception ignored) {}
            }
            if (finalCompletedStep == null && body.containsKey("completedStep")) {
                finalCompletedStep = String.valueOf(body.get("completedStep"));
            }
            if (body.containsKey("completedSteps") && body.get("completedSteps") instanceof java.util.List) {
                completedStepsList = ((java.util.List<?>) body.get("completedSteps")).stream()
                        .map(String::valueOf)
                        .toList();
            }
        }
        return ResponseEntity.ok(ApiResponse.<DirectorSetupProgressDto>builder()
                .success(true)
                .message("Director setup progress updated successfully")
                .data(academicService.updateDirectorSetupProgress(targetSchool, targetStep, finalCompletedStep, completedStepsList))
                .build());
    }

    // --- HOD Department Summary ---
    @GetMapping("/hod/department-summary")
    public ResponseEntity<ApiResponse<HodDepartmentSummaryDto>> getHodDepartmentSummary(
            @RequestParam(required = false) String hodEmail,
            java.security.Principal principal) {
        String email = (hodEmail != null && !hodEmail.isBlank())
                ? hodEmail
                : (principal != null ? principal.getName() : null);
        System.out.println("\n>>> [CONTROLLER] GET /api/v1/academic/hod/department-summary | hodEmail param: " + hodEmail + " | principal: " + (principal != null ? principal.getName() : "null") + " | resolved email: " + email);
        HodDepartmentSummaryDto data = academicService.getHodDepartmentSummary(email);
        System.out.println("<<< [CONTROLLER] GET /api/v1/academic/hod/department-summary SUCCESS | deptId: " + data.getDeptId() + " | deptName: " + data.getDeptName() + " | hodEmail: " + data.getHodEmail());
        return ResponseEntity.ok(ApiResponse.<HodDepartmentSummaryDto>builder()
                .success(true)
                .data(data)
                .build());
    }

    // --- HOD Setup Progress ---
    @GetMapping("/hod/setup-progress")
    public ResponseEntity<ApiResponse<HodSetupProgressDto>> getHodSetupProgress(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hodEmail,
            @RequestParam(required = false) String id,
            java.security.Principal principal) {
        String email = (hodEmail != null && !hodEmail.isBlank())
                ? hodEmail
                : (principal != null ? principal.getName() : null);
        String deptId = (departmentId != null && !departmentId.isBlank()) ? departmentId : id;
        System.out.println("\n>>> [CONTROLLER] GET /api/v1/academic/hod/setup-progress | deptId: " + deptId + " | email: " + email);
        HodSetupProgressDto data = academicService.getHodSetupProgress(deptId, email);
        System.out.println("<<< [CONTROLLER] GET /api/v1/academic/hod/setup-progress SUCCESS | currentStep: " + data.getCurrentStep() + " | status: " + data.getOverallStatus());
        return ResponseEntity.ok(ApiResponse.<HodSetupProgressDto>builder()
                .success(true)
                .data(data)
                .build());
    }

    @RequestMapping(value = "/hod/setup-progress", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<HodSetupProgressDto>> updateHodSetupProgress(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hodEmail,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) Integer step,
            @RequestParam(required = false) Integer currentStep,
            @RequestBody(required = false) java.util.Map<String, Object> body,
            java.security.Principal principal) {
        String deptId = departmentId != null ? departmentId : id;
        String email = (hodEmail != null && !hodEmail.isBlank())
                ? hodEmail
                : (principal != null ? principal.getName() : null);
        Integer targetStep = step != null ? step : currentStep;
        String completedStep = null;
        List<String> completedStepsList = null;

        if (body != null) {
            if (deptId == null && body.containsKey("identifier")) deptId = String.valueOf(body.get("identifier"));
            if (deptId == null && body.containsKey("departmentId")) deptId = String.valueOf(body.get("departmentId"));
            if (email == null && body.containsKey("email")) email = String.valueOf(body.get("email"));
            if (email == null && body.containsKey("hodEmail")) email = String.valueOf(body.get("hodEmail"));
            if (targetStep == null && body.containsKey("step")) {
                try { targetStep = Integer.parseInt(String.valueOf(body.get("step"))); } catch (Exception ignored) {}
            }
            if (targetStep == null && body.containsKey("currentStep")) {
                try { targetStep = Integer.parseInt(String.valueOf(body.get("currentStep"))); } catch (Exception ignored) {}
            }
            if (body.containsKey("completedStep")) {
                completedStep = String.valueOf(body.get("completedStep"));
            }
            if (body.get("completedSteps") instanceof List<?> list) {
                completedStepsList = list.stream().map(String::valueOf).toList();
            }
        }
        if (targetStep == null) targetStep = 1;
        System.out.println("\n>>> [CONTROLLER] POST/PUT /api/v1/academic/hod/setup-progress | deptId: " + deptId + " | step: " + targetStep + " | email: " + email);
        HodSetupProgressDto updated = academicService.updateHodSetupProgress(deptId, targetStep, completedStep, completedStepsList, email);
        System.out.println("<<< [CONTROLLER] POST/PUT /api/v1/academic/hod/setup-progress SUCCESS | newStep: " + updated.getCurrentStep() + " | completedSteps: " + updated.getCompletedSteps());
        return ResponseEntity.ok(ApiResponse.<HodSetupProgressDto>builder()
                .success(true)
                .message("HOD setup progress updated successfully")
                .data(updated)
                .build());
    }

    @PostMapping("/hod/setup-progress/complete")
    public ResponseEntity<ApiResponse<HodSetupProgressDto>> completeHodSetup(
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String hodEmail,
            @RequestParam(required = false) String id,
            java.security.Principal principal) {
        String email = (hodEmail != null && !hodEmail.isBlank())
                ? hodEmail
                : (principal != null ? principal.getName() : null);
        String deptId = (departmentId != null && !departmentId.isBlank()) ? departmentId : id;
        System.out.println("\n>>> [CONTROLLER] POST /api/v1/academic/hod/setup-progress/complete | deptId: " + deptId + " | email: " + email);
        HodSetupProgressDto completed = academicService.completeHodSetup(deptId, email);
        System.out.println("<<< [CONTROLLER] POST /api/v1/academic/hod/setup-progress/complete SUCCESS | status: " + completed.getOverallStatus());
        return ResponseEntity.ok(ApiResponse.<HodSetupProgressDto>builder()
                .success(true)
                .message("HOD setup marked as completed successfully")
                .data(completed)
                .build());
    }

    // --- MasterProgramme Coordinator Summary & Setup Progress ---
    @GetMapping("/coordinator/programme-summary")
    public ResponseEntity<ApiResponse<ProgrammeCoordinatorSummaryDto>> getProgrammeCoordinatorSummary(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeCoordinatorSummaryDto>builder()
                .success(true)
                .data(academicService.getProgrammeCoordinatorSummary(coordinatorEmail, masterProgrammeId))
                .build());
    }

    @GetMapping({"/master-programmes/{masterProgrammeId}/setup-progress", "/coordinator/setup-progress", "/programme-coordinator/setup-progress"})
    public ResponseEntity<ApiResponse<ProgrammeCoordinatorSetupProgressDto>> getProgrammeCoordinatorSetupProgress(
            @PathVariable(required = false) String masterProgrammeId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(name = "masterProgrammeId", required = false) String masterProgrammeIdParam,
            @RequestParam(required = false) String programmeBatchId) {
        String effectiveProgId = masterProgrammeId != null && !masterProgrammeId.isBlank() ? masterProgrammeId :
                (masterProgrammeIdParam != null && !masterProgrammeIdParam.isBlank() ? masterProgrammeIdParam : null);
        return ResponseEntity.ok(ApiResponse.<ProgrammeCoordinatorSetupProgressDto>builder()
                .success(true)
                .data(academicService.getProgrammeCoordinatorSetupProgress(coordinatorEmail, effectiveProgId, programmeBatchId))
                .build());
    }

    @RequestMapping(value = {"/coordinator/setup-progress", "/programme-coordinator/setup-progress", "/programmes/{masterProgrammeId}/setup-progress", "/master-programmes/{masterProgrammeId}/setup-progress"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeCoordinatorSetupProgressDto>> updateProgrammeCoordinatorSetupProgress(
            @PathVariable(required = false) String masterProgrammeId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(name = "masterProgrammeId", required = false) String masterProgrammeIdParam,
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) Integer currentStep,
            @RequestBody(required = false) Map<String, Object> body) {
        String effectiveProgId = masterProgrammeId != null && !masterProgrammeId.isBlank() ? masterProgrammeId :
                (masterProgrammeIdParam != null && !masterProgrammeIdParam.isBlank() ? masterProgrammeIdParam :
                        (body != null && body.containsKey("masterProgrammeId") ? String.valueOf(body.get("masterProgrammeId")) : null));

        String effectiveProgrammeBatchId = programmeBatchId != null && !programmeBatchId.isBlank() ? programmeBatchId :
                (body != null && body.containsKey("programmeBatchId") ? String.valueOf(body.get("programmeBatchId")) : null);

        String effectiveEmail = coordinatorEmail != null && !coordinatorEmail.isBlank() ? coordinatorEmail :
                (body != null && body.containsKey("coordinatorEmail") ? String.valueOf(body.get("coordinatorEmail")) : null);

        Integer stepToUse = currentStep;
        if (stepToUse == null && body != null && body.containsKey("currentStep")) {
            try {
                stepToUse = Integer.parseInt(String.valueOf(body.get("currentStep")));
            } catch (Exception ignored) {}
        }
        if (stepToUse == null) stepToUse = 0;

        return ResponseEntity.ok(ApiResponse.<ProgrammeCoordinatorSetupProgressDto>builder()
                .success(true)
                .message("MasterProgramme Coordinator setup progress updated successfully")
                .data(academicService.updateProgrammeCoordinatorSetupProgress(effectiveEmail, effectiveProgId, effectiveProgrammeBatchId, stepToUse, body))
                .build());
    }

    @RequestMapping(value = {"/coordinator/setup-progress/complete", "/programme-coordinator/setup-progress/complete", "/programmes/{masterProgrammeId}/setup-progress/complete", "/master-programmes/{masterProgrammeId}/setup-progress/complete"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeCoordinatorSetupProgressDto>> completeProgrammeCoordinatorSetup(
            @PathVariable(required = false) String masterProgrammeId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(name = "masterProgrammeId", required = false) String masterProgrammeIdParam,
            @RequestParam(required = false) String programmeBatchId) {
        String effectiveProgId = masterProgrammeId != null && !masterProgrammeId.isBlank() ? masterProgrammeId :
                (masterProgrammeIdParam != null && !masterProgrammeIdParam.isBlank() ? masterProgrammeIdParam : null);
        return ResponseEntity.ok(ApiResponse.<ProgrammeCoordinatorSetupProgressDto>builder()
                .success(true)
                .message("MasterProgramme Coordinator setup marked as completed successfully")
                .data(academicService.completeProgrammeCoordinatorSetup(coordinatorEmail, effectiveProgId, programmeBatchId))
                .build());
    }

    @GetMapping("/course-coordinator/summary")
    public ResponseEntity<ApiResponse<CourseCoordinatorSummaryDto>> getCourseCoordinatorSummary(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId) {
        String targetMasterCourseId = masterCourseId != null ? masterCourseId : (offeringId != null ? offeringId : (programmeBatchCourseId != null ? programmeBatchCourseId : programmeBatchCourseId));
        return ResponseEntity.ok(ApiResponse.<CourseCoordinatorSummaryDto>builder()
                .success(true)
                .data(academicService.getCourseCoordinatorSummary(coordinatorEmail, targetMasterCourseId))
                .build());
    }

    @GetMapping("/course-coordinator/setup-progress")
    public ResponseEntity<ApiResponse<CourseCoordinatorSetupProgressDto>> getCourseCoordinatorSetupProgress(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        String targetCourseId = (programmeBatchCourseId != null && !programmeBatchCourseId.isBlank()) ? programmeBatchCourseId
                : ((offeringId != null && !offeringId.isBlank()) ? offeringId : masterCourseId);
        String targetEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail
                : (principal != null ? principal.getName() : null);
        return ResponseEntity.ok(ApiResponse.<CourseCoordinatorSetupProgressDto>builder()
                .success(true)
                .data(academicService.getCourseCoordinatorSetupProgress(targetEmail, targetCourseId, programmeBatchId))
                .build());
    }

    @RequestMapping(value = {"/course-coordinator/setup-progress", "/course-coordinator/setup"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<CourseCoordinatorSetupProgressDto>> updateCourseCoordinatorSetupProgress(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestParam(required = false) Integer currentStep,
            @RequestParam(required = false) Integer stepNumber,
            @RequestParam(required = false) Integer step,
            @RequestBody(required = false) Map<String, Object> body,
            java.security.Principal principal) {
        String targetMasterCourseId = masterCourseId != null ? masterCourseId : (offeringId != null ? offeringId : (programmeBatchCourseId != null ? programmeBatchCourseId : programmeBatchCourseId));
        Integer targetStep = currentStep != null ? currentStep : (stepNumber != null ? stepNumber : step);
        String targetEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail
                : (principal != null ? principal.getName() : null);

        return ResponseEntity.ok(ApiResponse.<CourseCoordinatorSetupProgressDto>builder()
                .success(true)
                .message("Course Coordinator setup progress updated successfully")
                .data(academicService.updateCourseCoordinatorSetupProgress(targetEmail, targetMasterCourseId, targetStep, body))
                .build());
    }

    @RequestMapping(value = {"/course-coordinator/setup-progress/complete", "/course-coordinator/complete-setup"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<CourseCoordinatorSetupProgressDto>> completeCourseCoordinatorSetup(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestBody(required = false) Map<String, Object> body,
            java.security.Principal principal) {
        String targetMasterCourseId = masterCourseId != null ? masterCourseId : (offeringId != null ? offeringId : (programmeBatchCourseId != null ? programmeBatchCourseId : programmeBatchCourseId));
        String targetEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail
                : (principal != null ? principal.getName() : null);

        return ResponseEntity.ok(ApiResponse.<CourseCoordinatorSetupProgressDto>builder()
                .success(true)
                .message("Course Coordinator setup marked as completed successfully")
                .data(academicService.completeCourseCoordinatorSetup(targetEmail, targetMasterCourseId, body))
                .build());
    }

    // --- Schools ---
    @GetMapping("/schools")
    public ResponseEntity<ApiResponse<List<School>>> getSchools() {
        return ResponseEntity.ok(ApiResponse.<List<School>>builder()
                .success(true)
                .data(academicService.getAllSchools())
                .build());
    }

    @GetMapping("/schools/{id}")
    public ResponseEntity<ApiResponse<School>> getSchoolById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<School>builder()
                .success(true)
                .data(academicService.getSchoolById(id))
                .build());
    }

    @PostMapping("/schools")
    public ResponseEntity<ApiResponse<School>> saveSchool(@RequestBody School school) {
        return ResponseEntity.ok(ApiResponse.<School>builder()
                .success(true)
                .message("School saved successfully")
                .data(academicService.saveSchool(school))
                .build());
    }

    @PutMapping("/schools/{id}")
    public ResponseEntity<ApiResponse<School>> updateSchool(@PathVariable String id, @RequestBody School school) {
        return ResponseEntity.ok(ApiResponse.<School>builder()
                .success(true)
                .message("School updated successfully")
                .data(academicService.updateSchool(id, school))
                .build());
    }

    // --- Departments ---
    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<Department>>> getDepartments(@RequestParam(required = false) String schoolId) {
        requestScopeAuthorizer.assertRequestedSchool(schoolId);
        List<Department> list = (schoolId != null && !schoolId.isBlank())
                ? academicService.getDepartmentsBySchool(schoolId)
                : academicService.getAllDepartments();
        return ResponseEntity.ok(ApiResponse.<List<Department>>builder()
                .success(true)
                .data(list)
                .build());
    }

    @GetMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Department>> getDepartmentById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<Department>builder()
                .success(true)
                .data(academicService.getDepartmentById(id))
                .build());
    }

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<Department>> saveDepartment(@RequestBody Department department) {
        return ResponseEntity.ok(ApiResponse.<Department>builder()
                .success(true)
                .message("Department saved successfully")
                .data(academicService.saveDepartment(department))
                .build());
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Department>> updateDepartment(
            @PathVariable String id,
            @RequestBody Department department) {
        department.setId(id);
        return ResponseEntity.ok(ApiResponse.<Department>builder()
                .success(true)
                .message("Department updated successfully")
                .data(academicService.saveDepartment(department))
                .build());
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable String id) {
        academicService.deleteDepartment(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Department deleted").build());
    }

    // --- Programmes ---
    @GetMapping("/master-programmes")
    public ResponseEntity<ApiResponse<List<MasterProgramme>>> getProgrammes(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String level) {
        requestScopeAuthorizer.assertRequestedSchool(schoolId);
        requestScopeAuthorizer.assertRequestedDepartment(departmentId);
        List<MasterProgramme> list = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? academicService.getProgrammesByCoordinatorEmail(coordinatorEmail)
                : (departmentId != null && !departmentId.isBlank())
                ? academicService.getProgrammesByDepartment(departmentId)
                : (schoolId != null && !schoolId.isBlank())
                ? academicService.getProgrammesBySchool(schoolId)
                : academicService.getAllProgrammes();
        if (level != null && !level.isBlank()) {
            String normLevel = level.trim().toUpperCase();
            list = list.stream()
                    .filter(p -> normLevel.equalsIgnoreCase(p.getLevel()))
                    .toList();
        }
        return ResponseEntity.ok(ApiResponse.<List<MasterProgramme>>builder()
                .success(true)
                .data(list)
                .build());
    }

    @GetMapping("/master-programmes/coordinator")
    public ResponseEntity<ApiResponse<List<MasterProgramme>>> getProgrammesForCoordinator(
            @RequestParam(required = false) String coordinatorEmail,
            java.security.Principal principal) {
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail
                : (principal != null ? principal.getName() : null);
        List<MasterProgramme> list = academicService.getProgrammesByCoordinatorEmail(effectiveEmail);
        return ResponseEntity.ok(ApiResponse.<List<MasterProgramme>>builder()
                .success(true)
                .data(list)
                .build());
    }

    @GetMapping("/master-programmes/{id}")
    public ResponseEntity<ApiResponse<MasterProgramme>> getProgrammeById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<MasterProgramme>builder()
                .success(true)
                .data(academicService.getProgrammeById(id))
                .build());
    }

    @PostMapping("/master-programmes")
    public ResponseEntity<ApiResponse<MasterProgramme>> saveProgramme(@RequestBody MasterProgramme programme) {
        System.out.println("\n>>> [CONTROLLER] POST /api/v1/academic/master-programmes | id: " + (programme != null ? programme.getId() : "null") + " | name: " + (programme != null ? programme.getName() : "null") + " | coordinator: " + (programme != null ? programme.getCoordinator() : "null") + " | coordinatorEmail: " + (programme != null ? programme.getCoordinatorEmail() : "null"));
        MasterProgramme saved = academicService.saveProgramme(programme);
        System.out.println("<<< [CONTROLLER] POST /api/v1/academic/master-programmes SUCCESS | savedId: " + saved.getId() + " | coordinator: " + saved.getCoordinator() + " | coordinatorEmail: " + saved.getCoordinatorEmail());
        return ResponseEntity.ok(ApiResponse.<MasterProgramme>builder()
                .success(true)
                .message("MasterProgramme saved successfully")
                .data(saved)
                .build());
    }

    @PutMapping("/master-programmes/{id}")
    public ResponseEntity<ApiResponse<MasterProgramme>> updateProgramme(
            @PathVariable String id,
            @RequestBody MasterProgramme programme) {
        programme.setId(id);
        System.out.println("\n>>> [CONTROLLER] PUT /api/v1/academic/master-programmes/" + id + " | name: " + programme.getName() + " | coordinator: " + programme.getCoordinator() + " | coordinatorEmail: " + programme.getCoordinatorEmail());
        MasterProgramme saved = academicService.saveProgramme(programme);
        System.out.println("<<< [CONTROLLER] PUT /api/v1/academic/master-programmes/" + id + " SUCCESS | savedId: " + saved.getId() + " | coordinator: " + saved.getCoordinator() + " | coordinatorEmail: " + saved.getCoordinatorEmail());
        return ResponseEntity.ok(ApiResponse.<MasterProgramme>builder()
                .success(true)
                .message("MasterProgramme updated successfully")
                .data(saved)
                .build());
    }

    @PutMapping("/master-programmes/{id}/coordinator")
    public ResponseEntity<ApiResponse<MasterProgramme>> updateProgrammeCoordinator(
            @PathVariable String id,
            @RequestBody java.util.Map<String, Object> body) {
        String coordinator = body.containsKey("coordinator") ? String.valueOf(body.get("coordinator")) : (body.containsKey("coordinatorId") ? String.valueOf(body.get("coordinatorId")) : null);
        String coordinatorEmail = body.containsKey("coordinatorEmail") ? String.valueOf(body.get("coordinatorEmail")) : null;
        System.out.println("\n>>> [CONTROLLER] PUT /api/v1/academic/master-programmes/" + id + "/coordinator | coordinator: " + coordinator + " | email: " + coordinatorEmail);
        MasterProgramme prog = academicService.getProgrammeById(id);
        if (prog == null) {
            prog = MasterProgramme.builder().id(id).name("MasterProgramme " + id).degreeAwarded(id).build();
        }
        if (coordinator != null) prog.setCoordinator(coordinator);
        if (coordinatorEmail != null) prog.setCoordinatorEmail(coordinatorEmail);
        MasterProgramme saved = academicService.saveProgramme(prog);
        System.out.println("<<< [CONTROLLER] PUT /api/v1/academic/master-programmes/" + id + "/coordinator SUCCESS | coordinator: " + saved.getCoordinator());
        return ResponseEntity.ok(ApiResponse.<MasterProgramme>builder()
                .success(true)
                .message("MasterProgramme coordinator updated successfully")
                .data(saved)
                .build());
    }

    @DeleteMapping("/master-programmes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProgramme(@PathVariable String id) {
        System.out.println("\n>>> [CONTROLLER] DELETE /api/v1/academic/master-programmes/" + id);
        academicService.deleteProgramme(id);
        System.out.println("<<< [CONTROLLER] DELETE /api/v1/academic/master-programmes/" + id + " SUCCESS");
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("MasterProgramme deleted").build());
    }

    // --- Batches ---
    @GetMapping("/programme-batches")
    public ResponseEntity<ApiResponse<List<ProgrammeBatch>>> getBatches(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail,
            @RequestParam(required = false) String hodEmail,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            java.security.Principal principal) {
        List<ProgrammeBatch> batches = academicService.getBatchesFiltered(
                masterProgrammeId, departmentId, coordinatorEmail, courseCoordinatorEmail, hodEmail, userEmail, role, status);
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatch>>builder().success(true).data(batches).build());
    }

    @GetMapping("/programme-batches/course-coordinator")
    public ResponseEntity<ApiResponse<List<ProgrammeBatch>>> getBatchesByCourseCoordinator(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail,
            @RequestParam(required = false) String status,
            java.security.Principal principal) {
        String effectiveEmail = (courseCoordinatorEmail != null && !courseCoordinatorEmail.isBlank())
                ? courseCoordinatorEmail
                : ((coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : (principal != null ? principal.getName() : null));
        List<ProgrammeBatch> batches = academicService.getBatchesFiltered(
                null, null, null, effectiveEmail, null, null, null, status);
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatch>>builder()
                .success(true)
                .data(batches)
                .build());
    }

    @GetMapping("/programme-batches/{id}")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> getBatchById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .data(academicService.getBatchById(id))
                .build());
    }

    @GetMapping("/programme-batches/{programmeBatchId}/context")
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.BatchContextDto>> getBatchContext(@PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.BatchContextDto>builder()
                .success(true)
                .data(academicService.getBatchContext(programmeBatchId))
                .build());
    }

    @GetMapping("/programme-batches/{programmeBatchId}/courses")
    public ResponseEntity<ApiResponse<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>> getCoursesForBatch(@PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>builder()
                .success(true)
                .data(academicService.getProgrammeBatchCoursesByBatch(programmeBatchId))
                .build());
    }

    @PostMapping("/programme-batches/{programmeBatchId}/courses")
    public ResponseEntity<ApiResponse<com.dypiu.nba.entity.ProgrammeBatchCourse>> createCourseForBatch(
            @PathVariable String programmeBatchId,
            @RequestBody com.dypiu.nba.entity.ProgrammeBatchCourse course) {
        course.setProgrammeBatchId(programmeBatchId);
        com.dypiu.nba.entity.ProgrammeBatchCourse saved = academicService.saveProgrammeBatchCourse(course);
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.entity.ProgrammeBatchCourse>builder()
                .success(true)
                .message("Programme batch course created successfully")
                .data(saved)
                .build());
    }

    @PostMapping("/programme-batches")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> saveBatch(@RequestBody ProgrammeBatch batch) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("ProgrammeBatch saved successfully")
                .data(academicService.saveBatch(batch))
                .build());
    }

    @PutMapping("/programme-batches/{id}")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> updateBatch(
            @PathVariable String id,
            @RequestBody ProgrammeBatch batch) {
        batch.setId(id);
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("ProgrammeBatch updated successfully")
                .data(academicService.saveBatch(batch))
                .build());
    }

    @PostMapping("/programme-batches/{id}/status")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> changeBatchStatus(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch status updated successfully.")
                .data(batchLifecycleService.changeBatchStatus(id, payload.get("status"), payload.get("reason")))
                .build());
    }

    @PostMapping("/programme-batches/{id}/reopen")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> reopenBatch(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        java.time.ZonedDateTime until = java.time.ZonedDateTime.parse(payload.get("editingWindowUntil"));
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch reopened successfully.")
                .data(batchLifecycleService.reopenGraduatedBatch(id, until, payload.get("reason")))
                .build());
    }

    @PostMapping("/programme-batches/{id}/close-reopening")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> closeReopening(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch reopening window closed successfully.")
                .data(batchLifecycleService.closeReopeningWindow(id, payload.get("reason")))
                .build());
    }

    @DeleteMapping("/programme-batches/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable String id) {
        academicService.deleteBatch(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("ProgrammeBatch deleted").build());
    }

    // --- Semester Lifecycle ---
    @GetMapping({"/programme-batches/{id}/semesters/status", "/batches/{id}/semesters/status"})
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSemestersStatusOverview(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(academicService.getSemestersStatusOverview(id))
                .build());
    }

    @GetMapping({"/programme-batches/{id}/semesters/{semester}/readiness", "/batches/{id}/semesters/{semester}/readiness"})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.SemesterReadinessDto>> getSemesterReadiness(
            @PathVariable String id,
            @PathVariable Integer semester) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.SemesterReadinessDto>builder()
                .success(true)
                .data(academicService.getSemesterReadiness(id, semester))
                .build());
    }

    @PostMapping({"/programme-batches/{id}/semesters/{semester}/complete", "/batches/{id}/semesters/{semester}/complete"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> completeSemester(
            @PathVariable String id,
            @PathVariable Integer semester,
            @RequestBody(required = false) Map<String, String> payload) {
        String reason = (payload != null) ? payload.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Semester completed successfully.")
                .data(academicService.completeSemester(id, semester, reason))
                .build());
    }

    @PostMapping({"/programme-batches/{id}/semesters/{semester}/reopen", "/batches/{id}/semesters/{semester}/reopen"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> reopenSemester(
            @PathVariable String id,
            @PathVariable Integer semester,
            @RequestBody(required = false) Map<String, String> payload) {
        String reason = (payload != null) ? payload.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Semester reopened successfully.")
                .data(academicService.reopenSemester(id, semester, reason))
                .build());
    }

    // --- Programme-Batch ATR ---
    @GetMapping("/programme-batches/{programmeBatchId}/atr")
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeAtrReportDto>> getProgrammeBatchAtr(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeAtrReportDto>builder()
                .success(true)
                .data(atrService.getProgrammeAtrReport(batch.getMasterProgrammeId(), programmeBatchId))
                .build());
    }

    @RequestMapping(value = "/programme-batches/{programmeBatchId}/atr", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeAtrReportDto>> saveProgrammeBatchAtr(
            @PathVariable String programmeBatchId,
            @RequestBody com.dypiu.nba.dto.ProgrammeAtrReportDto dto) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        if (dto.getBatch() == null) {
            dto.setBatch(com.dypiu.nba.dto.ProgrammeAtrReportDto.BatchSummary.builder().id(programmeBatchId).name(batch != null ? batch.getName() : "").build());
        } else {
            dto.getBatch().setId(programmeBatchId);
        }
        if (dto.getProgramme() == null && batch != null) {
            dto.setProgramme(com.dypiu.nba.dto.ProgrammeAtrReportDto.ProgrammeSummary.builder().id(batch.getMasterProgrammeId()).build());
        } else if (batch != null) {
            dto.getProgramme().setId(batch.getMasterProgrammeId());
        }
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeAtrReportDto>builder()
                .success(true)
                .message("Programme Batch ATR saved successfully")
                .data(atrService.saveProgrammeAtrReport(dto))
                .build());
    }

    @PostMapping("/programme-batches/{programmeBatchId}/atr/submit")
    public ResponseEntity<ApiResponse<com.dypiu.nba.entity.ProgrammeAtr>> submitProgrammeBatchAtr(
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        String submitter = principal != null ? principal.getName() : "Programme Coordinator";
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.entity.ProgrammeAtr>builder()
                .success(true)
                .message("Programme Batch ATR submitted for verification")
                .data(atrService.submitProgrammeAtr(batch.getMasterProgrammeId(), programmeBatchId, submitter))
                .build());
    }

    @GetMapping("/master-courses")
    public ResponseEntity<ApiResponse<List<MasterCourse>>> getCourses(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String programmeBatchId) {
        String effectiveProgId = (masterProgrammeId != null && !masterProgrammeId.isBlank()) ? masterProgrammeId : masterProgrammeId;
        String effectiveProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId : programmeBatchId;
        List<MasterCourse> courses = (effectiveProgId != null && !effectiveProgId.isBlank())
            ? academicService.getCoursesByProgramme(effectiveProgId, effectiveProgrammeBatchId) 
            : academicService.getAllCourses();
        return ResponseEntity.ok(ApiResponse.<List<MasterCourse>>builder().success(true).data(courses).build());
    }

    @GetMapping("/master-courses/{id}")
    public ResponseEntity<ApiResponse<MasterCourse>> getCourseById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<MasterCourse>builder()
                .success(true)
                .data(academicService.getCourseById(id))
                .build());
    }

    @PostMapping("/master-courses")
    public ResponseEntity<ApiResponse<MasterCourse>> saveCourse(@RequestBody MasterCourse course) {
        return ResponseEntity.ok(ApiResponse.<MasterCourse>builder()
                .success(true)
                .message("MasterCourse saved successfully")
                .data(academicService.saveCourse(course))
                .build());
    }

    @PutMapping("/master-courses/{id}")
    public ResponseEntity<ApiResponse<MasterCourse>> updateCourse(
            @PathVariable String id,
            @RequestBody MasterCourse course) {
        course.setId(id);
        return ResponseEntity.ok(ApiResponse.<MasterCourse>builder()
                .success(true)
                .message("MasterCourse updated successfully")
                .data(academicService.saveCourse(course))
                .build());
    }

    @DeleteMapping("/master-courses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable String id) {
        academicService.deleteCourse(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("MasterCourse deleted").build());
    }

    // --- MasterCourse Offerings (ProgrammeBatch Specific) ---
    @GetMapping("/programme-batch-courses")
    public ResponseEntity<ApiResponse<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>> getProgrammeBatchCourses(
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail) {
        String targetProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId : null;
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : courseCoordinatorEmail;
        List<com.dypiu.nba.entity.ProgrammeBatchCourse> courses;
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            courses = academicService.getProgrammeBatchCoursesByCoordinatorEmail(effectiveEmail, targetProgrammeBatchId);
        } else {
            courses = academicService.getProgrammeBatchCoursesByBatch(targetProgrammeBatchId);
        }
        return ResponseEntity.ok(ApiResponse.<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>builder()
                .success(true)
                .data(courses != null ? courses : Collections.emptyList())
                .build());
    }

    @GetMapping({"/programme-batch-courses/batch/{batchId}", "/programme-batch-courses/batches/{batchId}"})
    public ResponseEntity<ApiResponse<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>> getProgrammeBatchCoursesByBatchId(
            @PathVariable String batchId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail) {
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : courseCoordinatorEmail;
        List<com.dypiu.nba.entity.ProgrammeBatchCourse> courses;
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            courses = academicService.getProgrammeBatchCoursesByCoordinatorEmail(effectiveEmail, batchId);
        } else {
            courses = academicService.getProgrammeBatchCoursesByBatch(batchId);
        }
        return ResponseEntity.ok(ApiResponse.<List<com.dypiu.nba.entity.ProgrammeBatchCourse>>builder()
                .success(true)
                .data(courses != null ? courses : Collections.emptyList())
                .build());
    }

    @GetMapping("/programme-batch-courses/{offeringId}")
    public ResponseEntity<ApiResponse<com.dypiu.nba.entity.ProgrammeBatchCourse>> getProgrammeBatchCourseById(@PathVariable String offeringId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.entity.ProgrammeBatchCourse>builder()
                .success(true)
                .data(academicService.getProgrammeBatchCourseById(offeringId))
                .build());
    }

    @PostMapping("/programme-batch-courses")
    public ResponseEntity<ApiResponse<com.dypiu.nba.entity.ProgrammeBatchCourse>> saveProgrammeBatchCourse(@RequestBody com.dypiu.nba.dto.CourseOfferingRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.entity.ProgrammeBatchCourse>builder()
                .success(true)
                .message("Course offering created successfully")
                .data(academicService.createCourseOffering(requestDto))
                .build());
    }

    @PutMapping("/programme-batch-courses/{offeringId}")
    public ResponseEntity<ApiResponse<com.dypiu.nba.entity.ProgrammeBatchCourse>> updateProgrammeBatchCourse(
            @PathVariable String offeringId,
            @RequestBody com.dypiu.nba.dto.CourseOfferingRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.entity.ProgrammeBatchCourse>builder()
                .success(true)
                .message("Course offering updated successfully")
                .data(academicService.updateCourseOffering(offeringId, requestDto))
                .build());
    }

    @DeleteMapping("/programme-batch-courses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProgrammeBatchCourse(@PathVariable String id) {
        academicService.deleteProgrammeBatchCourse(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("MasterCourse Offering deleted").build());
    }

    @GetMapping({"/course-outcomes", "/programme-batch-courses/{offeringId}/outcomes", "/programme-batch-courses/{offeringId}/course-outcomes"})
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> getOfferingOutcomes(
            @PathVariable(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestParam(required = false) String masterCourseId) {
        String effectiveId = (programmeBatchCourseId != null && !programmeBatchCourseId.isBlank())
                ? programmeBatchCourseId
                : ((offeringId != null && !offeringId.isBlank()) ? offeringId : masterCourseId);
        if (effectiveId != null && programmeBatchCourseRepository.existsById(effectiveId)) {
            return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                    .success(true)
                    .data(outcomeService.getOutcomesByOffering(effectiveId))
                    .build());
        }
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .data(outcomeService.getCOsByCourse(effectiveId))
                .build());
    }

    @RequestMapping(value = {"/programme-batch-courses/{offeringId}/outcomes", "/programme-batch-courses/{offeringId}/course-outcomes", "/course-outcomes"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> saveOfferingOutcomes(
            @PathVariable(required = false) String offeringId,
            @RequestParam(required = false) String programmeBatchCourseId,
            @RequestBody List<CourseOutcome> outcomes) {
        String effectiveId = (programmeBatchCourseId != null && !programmeBatchCourseId.isBlank()) ? programmeBatchCourseId : offeringId;
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .message("Course outcomes saved successfully")
                .data(outcomeService.saveCOs(effectiveId, outcomes))
                .build());
    }

    @DeleteMapping({"/programme-batch-courses/{offeringId}/outcomes/{coId}", "/programme-batch-courses/{offeringId}/course-outcomes/{coId}", "/course-outcomes/{coId}"})
    public ResponseEntity<ApiResponse<Void>> deleteOfferingOutcome(
            @PathVariable(required = false) String offeringId,
            @PathVariable String coId,
            @RequestParam(required = false) String programmeBatchCourseId) {
        String effectiveId = (programmeBatchCourseId != null && !programmeBatchCourseId.isBlank()) ? programmeBatchCourseId : offeringId;
        outcomeService.deleteCourseOutcome(effectiveId, coId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Course outcome deleted successfully")
                .build());
    }

    @GetMapping({"/programme-batch-courses/{offeringId}/mappings", "/programme-batch-courses/{offeringId}/co-po-pso-mappings"})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> getOfferingMappings(@PathVariable String offeringId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .data(outcomeService.getMappingsByOffering(offeringId))
                .build());
    }

    @PutMapping({"/programme-batch-courses/{offeringId}/mappings", "/programme-batch-courses/{offeringId}/co-po-pso-mappings"})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> saveOfferingMappings(
            @PathVariable String offeringId,
            @RequestBody com.dypiu.nba.dto.CourseMappingMatrixDto dto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .message("MasterCourse mappings saved for offering")
                .data(outcomeService.saveMappingsByOffering(offeringId, dto))
                .build());
    }

    @GetMapping({"/programme-batch-courses/{programmeBatchCourseId}/previous-year-atr", "/programme-batch-courses/{programmeBatchCourseId}/previous-batch-atr"})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseAtrReportDto>> getPreviousYearCourseAtr(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseAtrReportDto>builder()
                .success(true)
                .data(atrService.getPreviousBatchCourseAtrReport(programmeBatchCourseId))
                .build());
    }


    // --- Students ---
    @GetMapping("/programme-batches/{programmeBatchId}/students")
    public ResponseEntity<ApiResponse<List<Student>>> getStudentsByBatch(@PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<Student>>builder()
                .success(true)
                .data(academicService.getStudentsByBatch(programmeBatchId))
                .build());
    }

    @PostMapping("/programme-batches/{programmeBatchId}/students")
    public ResponseEntity<ApiResponse<Student>> saveStudent(@PathVariable String programmeBatchId, @RequestBody Student student) {
        student.setProgrammeBatchId(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<Student>builder()
                .success(true)
                .message("Student saved successfully")
                .data(academicService.saveStudent(student))
                .build());
    }

    @PutMapping("/programme-batches/{programmeBatchId}/students/{studentId}")
    public ResponseEntity<ApiResponse<Student>> updateStudent(
            @PathVariable String programmeBatchId,
            @PathVariable String studentId,
            @RequestBody Student student) {
        student.setId(studentId);
        student.setProgrammeBatchId(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<Student>builder()
                .success(true)
                .message("Student updated successfully")
                .data(academicService.saveStudent(student))
                .build());
    }

    @DeleteMapping({"/students/{id}", "/programme-batches/{programmeBatchId}/students/{id}"})
    public ResponseEntity<ApiResponse<Void>> deleteStudent(
            @PathVariable(required = false) String programmeBatchId,
            @PathVariable String id) {
        academicService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Student deleted successfully").build());
    }

    @RequestMapping(value = {"/programme-batches/{programmeBatchId}/coordinator", "/batches/{programmeBatchId}/coordinator"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeBatch>> assignProgrammeBatchCoordinator(
            @PathVariable String programmeBatchId,
            @RequestBody Map<String, Object> body) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        if (body != null) {
            if (body.containsKey("coordinatorId") && body.get("coordinatorId") != null) {
                try {
                    batch.setCoordinatorId(Long.parseLong(body.get("coordinatorId").toString()));
                } catch (Exception ignored) {}
            }
            if (body.containsKey("coordinatorName") && body.get("coordinatorName") != null) {
                batch.setCoordinatorName(body.get("coordinatorName").toString());
            }
            if (body.containsKey("coordinatorEmail") && body.get("coordinatorEmail") != null) {
                batch.setCoordinatorEmail(body.get("coordinatorEmail").toString());
            }
        }
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Programme Batch coordinator assigned successfully")
                .data(academicService.saveBatch(batch))
                .build());
    }

    @RequestMapping(value = "/master-programmes/{masterProgrammeId}/coordinator", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignMasterProgrammeCoordinator(
            @PathVariable String masterProgrammeId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = body != null ? new java.util.HashMap<>(body) : new java.util.HashMap<>();
        payload.put("masterProgrammeId", masterProgrammeId);
        payload.put("programmeId", masterProgrammeId);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Master Programme coordinator assigned successfully")
                .data(academicService.assignHodCoordinator(payload))
                .build());
    }

    // --- HOD Coordinators Management ---
    @GetMapping("/hod/coordinators")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getHodCoordinators(@RequestParam(required = false) String departmentId) {
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(academicService.getHodCoordinators(departmentId))
                .build());
    }

    @RequestMapping(value = "/hod/coordinators", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignHodCoordinator(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("MasterProgramme coordinator assigned successfully.")
                .data(academicService.assignHodCoordinator(body))
                .build());
    }

    // --- ProgrammeBatch Course Allocation ---
    @PostMapping({"/master-courses/allocate", "/courses/allocate", "/programme-batch-courses/allocate", "/allocations"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocateCourses(@RequestBody Map<String, Object> body) {
        String masterProgrammeId = body != null && body.get("masterProgrammeId") != null ? body.get("masterProgrammeId").toString().trim() : null;
        if (masterProgrammeId == null || masterProgrammeId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "MasterProgramme ID is required for course allocation.");
        }
        String programmeBatchId = body != null && body.get("programmeBatchId") != null ? body.get("programmeBatchId").toString().trim() : null;
        boolean submit = body != null && Boolean.TRUE.equals(body.get("submit"));
        List<Map<String, Object>> allocations = body != null && body.get("allocations") instanceof List
                ? (List<Map<String, Object>>) body.get("allocations")
                : Collections.emptyList();

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message(submit ? "MasterCourse allocations saved and submitted for review." : "MasterCourse allocations saved successfully.")
                .data(academicService.allocateCourses(masterProgrammeId, programmeBatchId, allocations, submit))
                .build());
    }

    // --- Consolidated Outcomes ---
    @GetMapping("/outcomes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConsolidatedOutcomes(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(academicService.getConsolidatedOutcomes(masterProgrammeId, programmeBatchId))
                .build());
    }

    @RequestMapping(value = "/outcomes", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveConsolidatedOutcomes(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String programmeBatchId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = (body != null) ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
        if (masterProgrammeId != null && !masterProgrammeId.isBlank() && !payload.containsKey("masterProgrammeId")) {
            payload.put("masterProgrammeId", masterProgrammeId.trim());
        }
        if (programmeBatchId != null && !programmeBatchId.isBlank() && !payload.containsKey("programmeBatchId")) {
            payload.put("programmeBatchId", programmeBatchId.trim());
        }
        Map<String, Object> result = academicService.saveConsolidatedOutcomes(payload);
        @SuppressWarnings("unchecked")
        Map<String, Object> outcomeData = (result != null && result.containsKey("data") && result.get("data") instanceof Map<?, ?>)
                ? (Map<String, Object>) result.get("data")
                : result;
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("Outcomes updated successfully.")
                .data(outcomeData)
                .build());
    }

    // --- CO Targets ---
    @GetMapping("/master-courses/{masterCourseId}/co-targets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCourseCoTargets(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(academicService.getCourseCoTargets(masterCourseId, programmeBatchId))
                .build());
    }

    @RequestMapping(value = "/courses/{masterCourseId}/co-targets", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveCourseCoTargets(
            @PathVariable String masterCourseId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("MasterCourse CO targets updated.")
                .data(academicService.saveCourseCoTargets(masterCourseId, body))
                .build());
    }

    // --- MasterCourse Outcomes by MasterCourse ID ---
    @GetMapping("/master-courses/{masterCourseId}/outcomes")
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> getCourseOutcomesByMasterCourseId(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .data(outcomeService.getCOsByCourse(masterCourseId))
                .build());
    }

    @RequestMapping(value = "/courses/{masterCourseId}/outcomes", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> saveCourseOutcomesByMasterCourseId(
            @PathVariable String masterCourseId,
            @RequestBody List<CourseOutcome> outcomes) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .message("MasterCourse outcomes saved successfully.")
                .data(outcomeService.saveCOs(masterCourseId, outcomes))
                .build());
    }

    // --- MasterCourse Mapping Matrix by MasterCourse ID ---
    @GetMapping("/master-courses/{masterCourseId}/mapping")
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> getCourseMappingByMasterCourseId(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .data(outcomeService.getCourseMappings(masterCourseId))
                .build());
    }

    @RequestMapping(value = "/courses/{masterCourseId}/mapping", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> saveCourseMappingByMasterCourseId(
            @PathVariable String masterCourseId,
            @RequestBody com.dypiu.nba.dto.CourseMappingMatrixDto dto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .message("MasterCourse mappings updated.")
                .data(outcomeService.saveCourseMappings(masterCourseId, dto))
                .build());
    }

    // --- MasterProgramme Targets by MasterProgramme ID ---
    @GetMapping("/master-programmes/{masterProgrammeId}/targets")
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeTargetDto>> getProgrammeTargetsByMasterProgrammeId(
            @PathVariable String masterProgrammeId,
            @RequestParam(required = false) String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeTargetDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeTargets(masterProgrammeId))
                .build());
    }

    @RequestMapping(value = "/programmes/{masterProgrammeId}/targets", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeTargetDto>> saveProgrammeTargetsByMasterProgrammeId(
            @PathVariable String masterProgrammeId,
            @RequestBody com.dypiu.nba.dto.ProgrammeTargetDto dto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeTargetDto>builder()
                .success(true)
                .message("MasterProgramme targets saved.")
                .data(outcomeService.saveProgrammeTargets(masterProgrammeId, dto))
                .build());
    }
}
