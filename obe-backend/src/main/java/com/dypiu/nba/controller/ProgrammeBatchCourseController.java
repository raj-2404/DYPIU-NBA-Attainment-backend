package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.AttainmentCalculationService;
import com.dypiu.nba.service.AttainmentReportService;
import com.dypiu.nba.service.OutcomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/programme-batch-courses", "/api/v1/programme-batch-courses"})
@RequiredArgsConstructor
public class ProgrammeBatchCourseController {

    private final AcademicService academicService;
    private final OutcomeService outcomeService;
    private final AttainmentCalculationService calculationService;
    private final AttainmentReportService attainmentReportService;
    private final AtrService atrService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgrammeBatchCourse>>> getAllProgrammeBatchCourses(
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail) {
        String targetProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId : null;
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : courseCoordinatorEmail;
        List<ProgrammeBatchCourse> courses;
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            courses = academicService.getProgrammeBatchCoursesByCoordinatorEmail(effectiveEmail, targetProgrammeBatchId);
        } else {
            courses = academicService.getProgrammeBatchCoursesByBatch(targetProgrammeBatchId);
        }
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatchCourse>>builder()
                .success(true)
                .data(courses != null ? courses : java.util.Collections.emptyList())
                .build());
    }

    @GetMapping({"/batch/{batchId}", "/batches/{batchId}"})
    public ResponseEntity<ApiResponse<List<ProgrammeBatchCourse>>> getProgrammeBatchCoursesByBatchId(
            @PathVariable String batchId,
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail) {
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : courseCoordinatorEmail;
        List<ProgrammeBatchCourse> courses;
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            courses = academicService.getProgrammeBatchCoursesByCoordinatorEmail(effectiveEmail, batchId);
        } else {
            courses = academicService.getProgrammeBatchCoursesByBatch(batchId);
        }
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatchCourse>>builder()
                .success(true)
                .data(courses != null ? courses : java.util.Collections.emptyList())
                .build());
    }

    @GetMapping("/coordinator")
    public ResponseEntity<ApiResponse<List<ProgrammeBatchCourse>>> getCoursesByCoordinator(
            @RequestParam(required = false) String coordinatorEmail,
            @RequestParam(required = false) String courseCoordinatorEmail,
            @RequestParam(required = false) String programmeBatchId) {
        String targetProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId : programmeBatchId;
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail : courseCoordinatorEmail;
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatchCourse>>builder()
                .success(true)
                .data(academicService.getProgrammeBatchCoursesByCoordinatorEmail(effectiveEmail, targetProgrammeBatchId))
                .build());
    }

    @GetMapping("/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<ProgrammeBatchCourse>> getProgrammeBatchCourseById(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchCourse>builder()
                .success(true)
                .data(academicService.getProgrammeBatchCourseById(programmeBatchCourseId))
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProgrammeBatchCourse>> createProgrammeBatchCourse(
            @RequestBody CourseOfferingRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchCourse>builder()
                .success(true)
                .message("ProgrammeBatchCourse created successfully")
                .data(academicService.createCourseOffering(requestDto))
                .build());
    }

    @PutMapping("/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<ProgrammeBatchCourse>> updateProgrammeBatchCourse(
            @PathVariable String programmeBatchCourseId,
            @RequestBody CourseOfferingRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchCourse>builder()
                .success(true)
                .message("ProgrammeBatchCourse updated successfully")
                .data(academicService.updateCourseOffering(programmeBatchCourseId, requestDto))
                .build());
    }

    @DeleteMapping("/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<Void>> deleteProgrammeBatchCourse(
            @PathVariable String programmeBatchCourseId) {
        academicService.deleteProgrammeBatchCourse(programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("ProgrammeBatchCourse deleted successfully")
                .build());
    }

    // --- Course Outcomes ---
    @GetMapping("/{programmeBatchCourseId}/course-outcomes")
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> getCourseOutcomes(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .data(outcomeService.getCOsByCourse(programmeBatchCourseId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchCourseId}/course-outcomes", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> saveCourseOutcomes(
            @PathVariable String programmeBatchCourseId,
            @RequestBody List<CourseOutcome> outcomes) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .message("Course outcomes saved successfully")
                .data(outcomeService.saveCOs(programmeBatchCourseId, outcomes))
                .build());
    }

    @DeleteMapping("/{programmeBatchCourseId}/course-outcomes/{coId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourseOutcome(
            @PathVariable String programmeBatchCourseId,
            @PathVariable String coId) {
        outcomeService.deleteCourseOutcome(programmeBatchCourseId, coId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Course outcome deleted successfully")
                .build());
    }

    // --- CO -> PO/PSO Mapping Matrix & Keywords ---
    @GetMapping("/{programmeBatchCourseId}/co-po-pso-mappings")
    public ResponseEntity<ApiResponse<CourseMappingMatrixDto>> getCourseMappings(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<CourseMappingMatrixDto>builder()
                .success(true)
                .data(outcomeService.getCourseMappings(programmeBatchCourseId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchCourseId}/co-po-pso-mappings", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<CourseMappingMatrixDto>> saveCourseMappings(
            @PathVariable String programmeBatchCourseId,
            @RequestBody CourseMappingMatrixDto dto) {
        return ResponseEntity.ok(ApiResponse.<CourseMappingMatrixDto>builder()
                .success(true)
                .message("Course mappings saved successfully")
                .data(outcomeService.saveCourseMappings(programmeBatchCourseId, dto))
                .build());
    }

    // --- Attainment Configuration ---
    @GetMapping("/{programmeBatchCourseId}/config")
    public ResponseEntity<ApiResponse<AttainmentConfiguration>> getAttainmentConfig(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<AttainmentConfiguration>builder()
                .success(true)
                .data(calculationService.getAttainmentConfig(programmeBatchCourseId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchCourseId}/config", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<AttainmentConfiguration>> saveAttainmentConfig(
            @PathVariable String programmeBatchCourseId,
            @RequestBody AttainmentConfiguration config) {
        return ResponseEntity.ok(ApiResponse.<AttainmentConfiguration>builder()
                .success(true)
                .message("Attainment configuration saved")
                .data(calculationService.saveAttainmentConfig(programmeBatchCourseId, config))
                .build());
    }

    // --- Course Attainment Main Report (Table 1, Table 2, Attainment Table) ---
    @GetMapping({
            "/{programmeBatchCourseId}/attainment-main",
            "/{programmeBatchCourseId}/co-attainment",
            "/{programmeBatchCourseId}/course-attainment"
    })
    public ResponseEntity<ApiResponse<CourseAttainmentReportDto>> getCourseAttainmentMainReport(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<CourseAttainmentReportDto>builder()
                .success(true)
                .message("Course CO attainment report fetched successfully")
                .data(attainmentReportService.getOrCreateCourseAttainmentReport(programmeBatchCourseId))
                .build());
    }

    @PostMapping("/{programmeBatchCourseId}/attainment-main/finalize")
    public ResponseEntity<ApiResponse<CourseAttainmentReportDto>> finalizeCourseAttainmentMainReport(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        String actorName = principal != null ? principal.getName() : "Course Coordinator";
        return ResponseEntity.ok(ApiResponse.<CourseAttainmentReportDto>builder()
                .success(true)
                .message("Course Attainment Report finalized successfully")
                .data(attainmentReportService.finalizeCourseReport(programmeBatchCourseId, actorName))
                .build());
    }

    // --- Programme-Batch-Course ATR ---
    @GetMapping("/{programmeBatchCourseId}/atr")
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> getCourseAtrReport(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<CourseAtrReportDto>builder()
                .success(true)
                .data(atrService.getCourseAtrReport(programmeBatchCourseId))
                .build());
    }

    @RequestMapping(value = "/{programmeBatchCourseId}/atr", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> saveCourseAtrReport(
            @PathVariable String programmeBatchCourseId,
            @RequestBody CourseAtrReportDto dto) {
        if (dto.getCourseOffering() == null) {
            dto.setCourseOffering(CourseAtrReportDto.CourseOfferingSummary.builder()
                    .id(programmeBatchCourseId)
                    .build());
        } else if (dto.getCourseOffering().getId() == null || dto.getCourseOffering().getId().isBlank()) {
            dto.getCourseOffering().setId(programmeBatchCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<CourseAtrReportDto>builder()
                .success(true)
                .message("Course ATR saved successfully")
                .data(atrService.saveCourseAtrReport(dto))
                .build());
    }

    @PostMapping("/{programmeBatchCourseId}/atr/submit")
    public ResponseEntity<ApiResponse<CourseAtr>> submitCourseAtr(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        String submitter = principal != null ? principal.getName() : "Course Coordinator";
        return ResponseEntity.ok(ApiResponse.<CourseAtr>builder()
                .success(true)
                .message("Course ATR submitted for verification")
                .data(atrService.submitCourseAtr(programmeBatchCourseId, submitter))
                .build());
    }

    @GetMapping({"/{programmeBatchCourseId}/previous-year-atr", "/{programmeBatchCourseId}/previous-batch-atr"})
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> getPreviousYearCourseAtr(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<CourseAtrReportDto>builder()
                .success(true)
                .data(atrService.getPreviousBatchCourseAtrReport(programmeBatchCourseId))
                .build());
    }

    // --- Examination Marks Upload ---
    @GetMapping("/{programmeBatchCourseId}/examination")
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> getExaminationAttainment(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Examination attainment fetched successfully")
                .data(calculationService.getExaminationAttainment(programmeBatchCourseId))
                .build());
    }

    @PostMapping(value = "/{programmeBatchCourseId}/examination/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> uploadExaminationMarks(
            @PathVariable String programmeBatchCourseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thresholdPercentage", required = false) BigDecimal thresholdPercentage,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            Principal principal) {
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Course Coordinator");
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Examination sheet saved and attainment calculated successfully")
                .data(calculationService.processAndSaveExaminationFile(programmeBatchCourseId, file, thresholdPercentage, uploader))
                .build());
    }

    @DeleteMapping("/{programmeBatchCourseId}/examination")
    public ResponseEntity<ApiResponse<Void>> deleteExaminationMarks(
            @PathVariable String programmeBatchCourseId) {
        calculationService.deleteExaminationData(programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Examination marks and uploaded sheet deleted successfully")
                .build());
    }

    // --- Course End Survey Upload ---
    @GetMapping("/{programmeBatchCourseId}/survey")
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> getSurveyAttainment(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey attainment fetched successfully")
                .data(calculationService.getSurveyAttainment(programmeBatchCourseId))
                .build());
    }

    @PostMapping("/{programmeBatchCourseId}/survey")
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> saveSurveyResponses(
            @PathVariable String programmeBatchCourseId,
            @RequestBody com.dypiu.nba.dto.SurveyMarksPayloadDto payload) {
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey responses saved successfully")
                .data(calculationService.calculateSurveyAttainment(programmeBatchCourseId, payload))
                .build());
    }

    @PostMapping(value = "/{programmeBatchCourseId}/survey/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> uploadSurveyResponses(
            @PathVariable String programmeBatchCourseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thresholdPercentage", required = false) BigDecimal thresholdPercentage,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            Principal principal) {
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Course Coordinator");
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey responses processed successfully")
                .data(calculationService.processAndSaveSurveyFile(programmeBatchCourseId, file, thresholdPercentage, uploader))
                .build());
    }

    @DeleteMapping("/{programmeBatchCourseId}/survey")
    public ResponseEntity<ApiResponse<Void>> deleteSurveyResponses(
            @PathVariable String programmeBatchCourseId) {
        calculationService.deleteSurveyData(programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Course End Survey responses and uploaded sheet deleted successfully")
                .build());
    }
}
