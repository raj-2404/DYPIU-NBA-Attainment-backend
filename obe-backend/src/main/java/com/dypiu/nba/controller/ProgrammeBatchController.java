package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.ProgrammeAtr;
import com.dypiu.nba.entity.ProgrammeBatch;
import com.dypiu.nba.entity.ProgrammeBatchCourse;
import com.dypiu.nba.entity.Student;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.AttainmentCalculationService;
import com.dypiu.nba.service.AttainmentReportService;
import com.dypiu.nba.service.OutcomeService;
import com.dypiu.nba.security.RequestScopeAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

import com.dypiu.nba.service.IndirectAssessmentService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/programme-batches", "/api/v1/programme-batches"})
@RequiredArgsConstructor
public class ProgrammeBatchController {

    private final AcademicService academicService;
    private final AttainmentCalculationService calculationService;
    private final AttainmentReportService attainmentReportService;
    private final AtrService atrService;
    private final OutcomeService outcomeService;
    private final com.dypiu.nba.service.BatchLifecycleService batchLifecycleService;
    private final RequestScopeAuthorizer requestScopeAuthorizer;
    private final IndirectAssessmentService indirectAssessmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgrammeBatch>>> getAllProgrammeBatches(
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

        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatch>>builder()
                .success(true)
                .data(batches)
                .build());
    }

    @GetMapping("/coordinator")
    public ResponseEntity<ApiResponse<List<ProgrammeBatch>>> getProgrammeBatchesByCoordinator(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String status,
            java.security.Principal principal) {
        List<ProgrammeBatch> batches = academicService.getBatchesFiltered(
                masterProgrammeId, null, coordinatorEmail, null, null, null, null, status);
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatch>>builder()
                .success(true)
                .data(batches)
                .build());
    }

    @GetMapping("/course-coordinator")
    public ResponseEntity<ApiResponse<List<ProgrammeBatch>>> getProgrammeBatchesByCourseCoordinator(
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

    @GetMapping("/{programmeBatchId}")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> getProgrammeBatchById(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .data(academicService.getBatchById(programmeBatchId))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProgrammeBatch>> createProgrammeBatch(
            @RequestBody ProgrammeBatch batch) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("ProgrammeBatch created successfully")
                .data(academicService.saveBatch(batch))
                .build());
    }

    @PutMapping("/{programmeBatchId}")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> updateProgrammeBatch(
            @PathVariable String programmeBatchId,
            @RequestBody ProgrammeBatch batch) {
        batch.setId(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("ProgrammeBatch updated successfully")
                .data(academicService.saveBatch(batch))
                .build());
    }

    @PostMapping("/{programmeBatchId}/status")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> changeBatchStatus(
            @PathVariable String programmeBatchId,
            @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch status updated successfully.")
                .data(batchLifecycleService.changeBatchStatus(programmeBatchId, payload.get("status"), payload.get("reason")))
                .build());
    }

    @PostMapping("/{programmeBatchId}/reopen")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> reopenBatch(
            @PathVariable String programmeBatchId,
            @RequestBody java.util.Map<String, String> payload) {
        java.time.ZonedDateTime until = java.time.ZonedDateTime.parse(payload.get("editingWindowUntil"));
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch reopened successfully.")
                .data(batchLifecycleService.reopenGraduatedBatch(programmeBatchId, until, payload.get("reason")))
                .build());
    }

    @PostMapping("/{programmeBatchId}/close-reopening")
    public ResponseEntity<ApiResponse<ProgrammeBatch>> closeReopening(
            @PathVariable String programmeBatchId,
            @RequestBody java.util.Map<String, String> payload) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatch>builder()
                .success(true)
                .message("Batch reopening window closed successfully.")
                .data(batchLifecycleService.closeReopeningWindow(programmeBatchId, payload.get("reason")))
                .build());
    }

    @DeleteMapping("/{programmeBatchId}")
    public ResponseEntity<ApiResponse<Void>> deleteProgrammeBatch(
            @PathVariable String programmeBatchId) {
        academicService.deleteBatch(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("ProgrammeBatch deleted successfully")
                .build());
    }

    @GetMapping("/{programmeBatchId}/programme-batch-courses")
    public ResponseEntity<ApiResponse<List<ProgrammeBatchCourse>>> getProgrammeBatchCourses(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatchCourse>>builder()
                .success(true)
                .data(academicService.getProgrammeBatchCoursesByBatch(programmeBatchId))
                .build());
    }

    @GetMapping("/{programmeBatchId}/students")
    public ResponseEntity<ApiResponse<List<Student>>> getStudentsByBatch(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<Student>>builder()
                .success(true)
                .data(academicService.getStudentsByBatch(programmeBatchId))
                .build());
    }

    @PostMapping("/{programmeBatchId}/students")
    public ResponseEntity<ApiResponse<Student>> addStudentToBatch(
            @PathVariable String programmeBatchId,
            @RequestBody Student student) {
        student.setProgrammeBatchId(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<Student>builder()
                .success(true)
                .message("Student added to ProgrammeBatch successfully")
                .data(academicService.saveStudent(student))
                .build());
    }

    @GetMapping("/{programmeBatchId}/targets")
    public ResponseEntity<ApiResponse<ProgrammeTargetDto>> getProgrammeTargets(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeTargetDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeTargets(programmeBatchId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchId}/targets", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeTargetDto>> saveProgrammeTargets(
            @PathVariable String programmeBatchId,
            @RequestBody ProgrammeTargetDto dto) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeTargetDto>builder()
                .success(true)
                .message("Programme targets saved successfully")
                .data(outcomeService.saveProgrammeTargets(programmeBatchId, dto))
                .build());
    }

    // --- Atomic Outcomes Bundle Endpoint (POs, PSOs, PEOs, Targets) ---
    @GetMapping({"/{programmeBatchId}/outcomes-bundle", "/{programmeBatchId}/bundle", "/{programmeBatchId}/outcomes"})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>> getProgrammeBatchOutcomeBundle(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeBatchOutcomeBundle(programmeBatchId))
                .build());
    }

    @RequestMapping(value = {"/{programmeBatchId}/outcomes-bundle", "/{programmeBatchId}/bundle", "/{programmeBatchId}/outcomes"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>> saveProgrammeBatchOutcomeBundle(
            @PathVariable String programmeBatchId,
            @RequestBody com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto dto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>builder()
                .success(true)
                .message("Programme batch outcomes and targets saved successfully")
                .data(outcomeService.saveProgrammeBatchOutcomeBundle(programmeBatchId, dto))
                .build());
    }

    // --- Report 1: Average Mapping Report ---
    @GetMapping("/{programmeBatchId}/reports/average-mapping")
    public ResponseEntity<ApiResponse<Object>> getAverageMappingReport(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(batch.getMasterProgrammeId(), programmeBatchId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(java.util.Map.of(
                        "poMappings", report.getReport1AverageMappingPO() != null ? report.getReport1AverageMappingPO() : java.util.Collections.emptyList(),
                        "psoMappings", report.getReport1AverageMappingPSO() != null ? report.getReport1AverageMappingPSO() : java.util.Collections.emptyList()
                ))
                .build());
    }

    // --- Report 2: Direct Attainment Report ---
    @GetMapping("/{programmeBatchId}/reports/direct-attainment")
    public ResponseEntity<ApiResponse<Object>> getDirectAttainmentReport(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(batch.getMasterProgrammeId(), programmeBatchId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(java.util.Map.of(
                        "poDirectAttainment", report.getReport2DirectAttainmentPO() != null ? report.getReport2DirectAttainmentPO() : java.util.Collections.emptyList(),
                        "psoDirectAttainment", report.getReport2DirectAttainmentPSO() != null ? report.getReport2DirectAttainmentPSO() : java.util.Collections.emptyList()
                ))
                .build());
    }

    // --- Report 3: Indirect Attainment Report ---
    @GetMapping("/{programmeBatchId}/reports/indirect-attainment")
    public ResponseEntity<ApiResponse<Object>> getIndirectAttainmentReport(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(batch.getMasterProgrammeId(), programmeBatchId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(java.util.Map.of(
                        "poIndirectAttainment", report.getReport3IndirectAttainmentPO() != null ? report.getReport3IndirectAttainmentPO() : java.util.Collections.emptyList(),
                        "psoIndirectAttainment", report.getReport3IndirectAttainmentPSO() != null ? report.getReport3IndirectAttainmentPSO() : java.util.Collections.emptyList()
                ))
                .build());
    }

    // --- Report 4: Overall Attainment Report ---
    @GetMapping("/{programmeBatchId}/reports/overall-attainment")
    public ResponseEntity<ApiResponse<Object>> getOverallAttainmentReport(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(batch.getMasterProgrammeId(), programmeBatchId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(java.util.Map.of(
                        "poOverallAttainment", report.getReport4OverallAttainmentPO() != null ? report.getReport4OverallAttainmentPO() : java.util.Collections.emptyList(),
                        "psoOverallAttainment", report.getReport4OverallAttainmentPSO() != null ? report.getReport4OverallAttainmentPSO() : java.util.Collections.emptyList()
                ))
                .build());
    }

    // --- Programme Batch Main Attainment Report ---
    @GetMapping("/{programmeBatchId}/reports/attainment-main")
    public ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> getProgrammeBatchAttainmentMainReport(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchAttainmentReportDto>builder()
                .success(true)
                .data(attainmentReportService.getOrCreateProgrammeAttainmentReport(batch.getMasterProgrammeId(), programmeBatchId))
                .build());
    }

    @PostMapping("/{programmeBatchId}/reports/finalize")
    public ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> finalizeProgrammeBatchAttainmentReport(
            @PathVariable String programmeBatchId,
            Principal principal) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        String actorName = principal != null ? principal.getName() : "Programme Coordinator";
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchAttainmentReportDto>builder()
                .success(true)
                .message("Programme Batch Attainment Report finalized successfully")
                .data(attainmentReportService.finalizeProgrammeReport(batch.getMasterProgrammeId(), programmeBatchId, actorName))
                .build());
    }

    // --- Programme-Batch ATR ---
    @GetMapping("/{programmeBatchId}/atr")
    public ResponseEntity<ApiResponse<ProgrammeAtrReportDto>> getProgrammeBatchAtr(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<ProgrammeAtrReportDto>builder()
                .success(true)
                .data(atrService.getProgrammeAtrReport(batch.getMasterProgrammeId(), programmeBatchId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchId}/atr", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeAtrReportDto>> saveProgrammeBatchAtr(
            @PathVariable String programmeBatchId,
            @RequestBody ProgrammeAtrReportDto dto) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        if (dto.getBatch() == null) {
            dto.setBatch(ProgrammeAtrReportDto.BatchSummary.builder().id(programmeBatchId).name(batch != null ? batch.getName() : "").build());
        } else if (dto.getBatch().getId() == null || dto.getBatch().getId().isBlank()) {
            dto.getBatch().setId(programmeBatchId);
        }
        if (dto.getProgramme() == null && batch != null) {
            dto.setProgramme(ProgrammeAtrReportDto.ProgrammeSummary.builder().id(batch.getMasterProgrammeId()).build());
        }
        return ResponseEntity.ok(ApiResponse.<ProgrammeAtrReportDto>builder()
                .success(true)
                .message("Programme Batch ATR saved successfully")
                .data(atrService.saveProgrammeAtrReport(dto))
                .build());
    }

    @PostMapping("/{programmeBatchId}/atr/submit")
    public ResponseEntity<ApiResponse<ProgrammeAtr>> submitProgrammeBatchAtr(
            @PathVariable String programmeBatchId,
            Principal principal) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        String submitter = principal != null ? principal.getName() : "Programme Coordinator";
        return ResponseEntity.ok(ApiResponse.<ProgrammeAtr>builder()
                .success(true)
                .message("Programme Batch ATR submitted for verification")
                .data(atrService.submitProgrammeAtr(batch.getMasterProgrammeId(), programmeBatchId, submitter))
                .build());
    }

    // --- Programme End Survey Endpoints (GET, POST upload, POST manual save, DELETE) ---
    @GetMapping({"/{programmeBatchId}/survey", "/{programmeBatchId}/indirect-attainment"})
    public ResponseEntity<ApiResponse<ProgrammeSurveyResultDto>> getProgrammeSurvey(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<ProgrammeSurveyResultDto>builder()
                .success(true)
                .message("Programme exit survey fetched successfully")
                .data(calculationService.getProgrammeSurveyResult(batch.getMasterProgrammeId(), programmeBatchId))
                .build());
    }

    @PostMapping(value = {"/{programmeBatchId}/survey/upload", "/{programmeBatchId}/indirect-attainment/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProgrammeSurveyResultDto>> uploadProgrammeSurvey(
            @PathVariable String programmeBatchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            Principal principal) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Programme Coordinator");
        return ResponseEntity.ok(ApiResponse.<ProgrammeSurveyResultDto>builder()
                .success(true)
                .message("Programme exit survey processed successfully")
                .data(calculationService.processAndSaveProgrammeSurveyFile(batch.getMasterProgrammeId(), programmeBatchId, file, uploader))
                .build());
    }

    @PostMapping({"/{programmeBatchId}/survey", "/{programmeBatchId}/indirect-attainment"})
    public ResponseEntity<ApiResponse<ProgrammeSurveyResultDto>> saveProgrammeSurvey(
            @PathVariable String programmeBatchId,
            @RequestBody ProgrammeSurveyResultDto payload) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<ProgrammeSurveyResultDto>builder()
                .success(true)
                .message("Programme exit survey saved successfully")
                .data(calculationService.saveProgrammeSurveyResult(batch.getMasterProgrammeId(), programmeBatchId, payload))
                .build());
    }

    @DeleteMapping({"/{programmeBatchId}/survey", "/{programmeBatchId}/indirect-attainment"})
    public ResponseEntity<ApiResponse<Void>> deleteProgrammeSurvey(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        calculationService.deleteProgrammeSurvey(batch.getMasterProgrammeId(), programmeBatchId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Programme exit survey deleted successfully")
                .build());
    }

    // --- Surveys & Co-Curricular Events (Indirect Assessments) Endpoints ---

    @GetMapping("/{programmeBatchId}/indirect-assessments")
    public ResponseEntity<ApiResponse<List<IndirectAssessmentDto>>> getIndirectAssessments(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<List<IndirectAssessmentDto>>builder()
                .success(true)
                .message("Indirect assessments retrieved successfully")
                .data(indirectAssessmentService.getAssessments(programmeBatchId))
                .build());
    }

    @GetMapping("/{programmeBatchId}/indirect-assessments/{id}")
    public ResponseEntity<ApiResponse<IndirectAssessmentDto>> getIndirectAssessmentById(
            @PathVariable String programmeBatchId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<IndirectAssessmentDto>builder()
                .success(true)
                .data(indirectAssessmentService.getAssessmentById(programmeBatchId, id))
                .build());
    }

    @PostMapping("/{programmeBatchId}/indirect-assessments")
    public ResponseEntity<ApiResponse<IndirectAssessmentDto>> createIndirectAssessment(
            @PathVariable String programmeBatchId,
            @RequestBody IndirectAssessmentDto dto,
            Principal principal) {
        String user = principal != null ? principal.getName() : "System";
        return ResponseEntity.ok(ApiResponse.<IndirectAssessmentDto>builder()
                .success(true)
                .message("Survey / Co-Curricular Event indirect assessment created successfully")
                .data(indirectAssessmentService.createAssessment(programmeBatchId, dto, user))
                .build());
    }

    @PutMapping("/{programmeBatchId}/indirect-assessments/{id}")
    public ResponseEntity<ApiResponse<IndirectAssessmentDto>> updateIndirectAssessment(
            @PathVariable String programmeBatchId,
            @PathVariable String id,
            @RequestBody IndirectAssessmentDto dto,
            Principal principal) {
        String user = principal != null ? principal.getName() : "System";
        return ResponseEntity.ok(ApiResponse.<IndirectAssessmentDto>builder()
                .success(true)
                .message("Survey / Co-Curricular Event indirect assessment updated successfully")
                .data(indirectAssessmentService.updateAssessment(programmeBatchId, id, dto, user))
                .build());
    }

    @DeleteMapping("/{programmeBatchId}/indirect-assessments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIndirectAssessment(
            @PathVariable String programmeBatchId,
            @PathVariable String id) {
        indirectAssessmentService.deleteAssessment(programmeBatchId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Survey / Co-Curricular Event indirect assessment deleted successfully")
                .build());
    }

    @GetMapping("/{programmeBatchId}/indirect-assessments/consolidated")
    public ResponseEntity<ApiResponse<ConsolidatedIndirectAttainmentDto>> getConsolidatedIndirectAttainment(
            @PathVariable String programmeBatchId) {
        ProgrammeBatch batch = academicService.getBatchById(programmeBatchId);
        ProgrammeSurveyResultDto exitSurvey = calculationService.getProgrammeSurveyResult(batch.getMasterProgrammeId(), programmeBatchId);
        Map<String, BigDecimal> exitScores = new HashMap<>();
        if (exitSurvey != null) {
            if (exitSurvey.getPoIndirectAttainment() != null) {
                for (ProgrammeSurveyResultDto.PoIndirectItem it : exitSurvey.getPoIndirectAttainment()) {
                    exitScores.put(it.getPoCode().toUpperCase(), it.getIndirectAttainment());
                }
            }
            if (exitSurvey.getPsoIndirectAttainment() != null) {
                for (ProgrammeSurveyResultDto.PsoIndirectItem it : exitSurvey.getPsoIndirectAttainment()) {
                    exitScores.put(it.getPsoCode().toUpperCase(), it.getIndirectAttainment());
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.<ConsolidatedIndirectAttainmentDto>builder()
                .success(true)
                .message("Consolidated indirect attainment computed successfully")
                .data(indirectAssessmentService.getConsolidatedIndirectAttainment(programmeBatchId, exitScores))
                .build());
    }
}
