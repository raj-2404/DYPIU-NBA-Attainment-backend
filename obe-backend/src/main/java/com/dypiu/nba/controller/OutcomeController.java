package com.dypiu.nba.controller;

import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.dto.ProgrammeTargetDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.service.OutcomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/outcomes", "/api/v1/outcomes"})
@RequiredArgsConstructor
public class OutcomeController {

    private final OutcomeService outcomeService;

    @GetMapping({"/master-programmes/{masterProgrammeId}/pos", "/programme-batches/{masterProgrammeId}/pos", "/batches/{masterProgrammeId}/pos"})
    public ResponseEntity<ApiResponse<List<ProgrammeOutcome>>> getPOs(@PathVariable String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeOutcome>>builder()
                .success(true)
                .data(outcomeService.getPOsByProgramme(masterProgrammeId))
                .build());
    }

    @RequestMapping(value = {"/master-programmes/{masterProgrammeId}/pos", "/programme-batches/{masterProgrammeId}/pos", "/batches/{masterProgrammeId}/pos"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<ProgrammeOutcome>>> savePOs(@PathVariable String masterProgrammeId, @RequestBody List<ProgrammeOutcome> pos) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeOutcome>>builder()
                .success(true)
                .message("POs saved successfully")
                .data(outcomeService.savePOs(masterProgrammeId, pos))
                .build());
    }

    @GetMapping({"/master-programmes/{masterProgrammeId}/psos", "/programme-batches/{masterProgrammeId}/psos", "/batches/{masterProgrammeId}/psos"})
    public ResponseEntity<ApiResponse<List<ProgrammeSpecificOutcome>>> getPSOs(@PathVariable String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeSpecificOutcome>>builder()
                .success(true)
                .data(outcomeService.getPSOsByProgramme(masterProgrammeId))
                .build());
    }

    @RequestMapping(value = {"/master-programmes/{masterProgrammeId}/psos", "/programme-batches/{masterProgrammeId}/psos", "/batches/{masterProgrammeId}/psos"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<ProgrammeSpecificOutcome>>> savePSOs(@PathVariable String masterProgrammeId, @RequestBody List<ProgrammeSpecificOutcome> psos) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeSpecificOutcome>>builder()
                .success(true)
                .message("PSOs saved successfully")
                .data(outcomeService.savePSOs(masterProgrammeId, psos))
                .build());
    }

    @GetMapping({"/master-programmes/{masterProgrammeId}/peos", "/programme-batches/{masterProgrammeId}/peos", "/batches/{masterProgrammeId}/peos"})
    public ResponseEntity<ApiResponse<List<PeoOutcome>>> getPEOs(@PathVariable String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<List<PeoOutcome>>builder()
                .success(true)
                .data(outcomeService.getPEOsByProgramme(masterProgrammeId))
                .build());
    }

    @RequestMapping(value = {"/master-programmes/{masterProgrammeId}/peos", "/programme-batches/{masterProgrammeId}/peos", "/batches/{masterProgrammeId}/peos"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<PeoOutcome>>> savePEOs(@PathVariable String masterProgrammeId, @RequestBody List<PeoOutcome> peos) {
        return ResponseEntity.ok(ApiResponse.<List<PeoOutcome>>builder()
                .success(true)
                .message("PEOs saved successfully")
                .data(outcomeService.savePEOs(masterProgrammeId, peos))
                .build());
    }

    @GetMapping("/master-courses/{masterCourseId}/cos")
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> getCOs(@PathVariable String masterCourseId) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .data(outcomeService.getCOsByCourse(masterCourseId))
                .build());
    }

    @RequestMapping(value = "/master-courses/{masterCourseId}/cos", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<List<CourseOutcome>>> saveCOs(@PathVariable String masterCourseId, @RequestBody List<CourseOutcome> cos) {
        return ResponseEntity.ok(ApiResponse.<List<CourseOutcome>>builder()
                .success(true)
                .message("COs saved successfully")
                .data(outcomeService.saveCOs(masterCourseId, cos))
                .build());
    }

    // --- Target Benchmark Levels ---
    @GetMapping({"/master-programmes/{masterProgrammeId}/targets", "/programme-batches/{masterProgrammeId}/targets", "/batches/{masterProgrammeId}/targets"})
    public ResponseEntity<ApiResponse<ProgrammeTargetDto>> getProgrammeTargets(@PathVariable String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeTargetDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeTargets(masterProgrammeId))
                .build());
    }

    @RequestMapping(value = {"/master-programmes/{masterProgrammeId}/targets", "/programme-batches/{masterProgrammeId}/targets", "/batches/{masterProgrammeId}/targets"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<ProgrammeTargetDto>> saveProgrammeTargets(@PathVariable String masterProgrammeId, @RequestBody ProgrammeTargetDto targets) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeTargetDto>builder()
                .success(true)
                .message("Programme targets saved successfully")
                .data(outcomeService.saveProgrammeTargets(masterProgrammeId, targets))
                .build());
    }

    // --- CO to PO/PSO Mapping Matrix ---
    @GetMapping("/master-courses/{masterCourseId}/mappings")
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> getCourseMappings(@PathVariable String masterCourseId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .data(outcomeService.getCourseMappings(masterCourseId))
                .build());
    }

    @RequestMapping(value = "/master-courses/{masterCourseId}/mappings", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.CourseMappingMatrixDto>> saveCourseMappings(@PathVariable String masterCourseId, @RequestBody com.dypiu.nba.dto.CourseMappingMatrixDto dto) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.CourseMappingMatrixDto>builder()
                .success(true)
                .message("Course mappings saved successfully")
                .data(outcomeService.saveCourseMappings(masterCourseId, dto))
                .build());
    }

    // --- Atomic Bundle Endpoints (POs, PSOs, PEOs, and Targets in single atomic request) ---
    @GetMapping({
            "/programme-batches/{programmeBatchId}",
            "/programme-batches/{programmeBatchId}/bundle",
            "/programme-batches/{programmeBatchId}/outcomes",
            "/master-programmes/{programmeBatchId}/bundle",
            "/master-programmes/{programmeBatchId}/outcomes",
            "/batches/{programmeBatchId}/bundle",
            "/batches/{programmeBatchId}/outcomes"
    })
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>> getProgrammeBatchOutcomeBundle(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeBatchOutcomeBundle(programmeBatchId))
                .build());
    }

    @GetMapping({
            "/programme-batches/{programmeBatchId}/review",
            "/programme-batches/{programmeBatchId}/bundle/review",
            "/batches/{programmeBatchId}/review",
            "/batches/{programmeBatchId}/bundle/review",
            "/master-programmes/{programmeBatchId}/review"
    })
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>> getProgrammeBatchOutcomeReviewBundle(
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>builder()
                .success(true)
                .data(outcomeService.getProgrammeBatchOutcomeReviewBundle(programmeBatchId))
                .build());
    }

    @RequestMapping(
            value = {
                    "/programme-batches/{programmeBatchId}",
                    "/programme-batches/{programmeBatchId}/bundle",
                    "/programme-batches/{programmeBatchId}/outcomes",
                    "/master-programmes/{programmeBatchId}/bundle",
                    "/master-programmes/{programmeBatchId}/outcomes",
                    "/batches/{programmeBatchId}/bundle",
                    "/batches/{programmeBatchId}/outcomes"
            },
            method = {RequestMethod.POST, RequestMethod.PUT}
    )
    public ResponseEntity<ApiResponse<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>> saveProgrammeBatchOutcomeBundle(
            @PathVariable String programmeBatchId,
            @RequestBody com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto bundle) {
        return ResponseEntity.ok(ApiResponse.<com.dypiu.nba.dto.ProgrammeBatchOutcomeBundleDto>builder()
                .success(true)
                .message("Programme batch outcomes and targets saved successfully")
                .data(outcomeService.saveProgrammeBatchOutcomeBundle(programmeBatchId, bundle))
                .build());
    }
}
