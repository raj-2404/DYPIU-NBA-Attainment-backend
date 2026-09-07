package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.AttainmentCalculationService;
import com.dypiu.nba.service.ReportAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final AtrService atrService;
    private final com.dypiu.nba.service.AttainmentReportService attainmentReportService;
    private final ReportAccessService reportAccessService;
    private final AcademicService academicService;
    private final AttainmentCalculationService attainmentCalculationService;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final CourseAtrRepository courseAtrRepository;
    private final ProgrammeAtrRepository programmeAtrRepository;
    private final com.dypiu.nba.service.AttainmentReportExportService exportService;
    private final com.dypiu.nba.service.OutcomeService outcomeService;

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<ReportFiltersDto>> getReportFilters(Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        return ResponseEntity.ok(ApiResponse.<ReportFiltersDto>builder()
                .success(true)
                .data(reportAccessService.getReportFilters(user))
                .build());
    }

    // --- MasterCourse ATR Endpoints ---

    @GetMapping("/course-atr/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> getCourseAtrReport(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseAtrAccess(user, programmeBatchCourseId);

        return ResponseEntity.ok(ApiResponse.<CourseAtrReportDto>builder()
                .success(true)
                .data(atrService.getCourseAtrReport(programmeBatchCourseId))
                .build());
    }

    @GetMapping("/course-atr/{programmeBatchCourseId}/export-data")
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> getCourseAtrExportData(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        return getCourseAtrReport(programmeBatchCourseId, principal);
    }

    @PostMapping("/course-atr")
    public ResponseEntity<ApiResponse<CourseAtrReportDto>> saveCourseAtrReport(
            @RequestBody CourseAtrReportDto dto,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        if (dto.getCourseOffering() != null) {
            reportAccessService.validateCourseAtrAccess(user, dto.getCourseOffering().getId());
        }

        return ResponseEntity.ok(ApiResponse.<CourseAtrReportDto>builder()
                .success(true)
                .message("MasterCourse ATR saved successfully")
                .data(atrService.saveCourseAtrReport(dto))
                .build());
    }

    @PostMapping("/course-atr/{programmeBatchCourseId}/submit")
    public ResponseEntity<ApiResponse<CourseAtr>> submitCourseAtr(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseAtrAccess(user, programmeBatchCourseId);

        String submitter = user != null ? user.getName() : "MasterCourse Coordinator";
        return ResponseEntity.ok(ApiResponse.<CourseAtr>builder()
                .success(true)
                .message("MasterCourse ATR submitted for verification")
                .data(atrService.submitCourseAtr(programmeBatchCourseId, submitter))
                .build());
    }

    @GetMapping("/course-atrs")
    public ResponseEntity<ApiResponse<List<CourseAtrReportDto>>> listCourseAtrs(
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String masterCourseId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);

        List<ProgrammeBatchCourse> offerings;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            offerings = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
        } else if (masterCourseId != null && !masterCourseId.isBlank()) {
            offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
        } else {
            offerings = programmeBatchCourseRepository.findAll();
        }

        List<CourseAtrReportDto> reports = offerings.stream()
                .filter(o -> {
                    try {
                        reportAccessService.validateCourseOfferingAccess(user, o.getId());
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(o -> atrService.getCourseAtrReport(o.getId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<CourseAtrReportDto>>builder()
                .success(true)
                .data(reports)
                .build());
    }

    // --- MasterProgramme ATR Endpoints ---

    @GetMapping("/programme-atr/{masterProgrammeId}/batch/{programmeBatchId}")
    public ResponseEntity<ApiResponse<ProgrammeAtrReportDto>> getProgrammeAtrReport(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateProgrammeAtrAccess(user, masterProgrammeId, programmeBatchId);

        return ResponseEntity.ok(ApiResponse.<ProgrammeAtrReportDto>builder()
                .success(true)
                .data(atrService.getProgrammeAtrReport(masterProgrammeId, programmeBatchId))
                .build());
    }

    @GetMapping("/programme-atr/{masterProgrammeId}/batch/{programmeBatchId}/export-data")
    public ResponseEntity<ApiResponse<ProgrammeAtrReportDto>> getProgrammeAtrExportData(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId,
            Principal principal) {
        return getProgrammeAtrReport(masterProgrammeId, programmeBatchId, principal);
    }

    @PostMapping("/programme-atr")
    public ResponseEntity<ApiResponse<ProgrammeAtrReportDto>> saveProgrammeAtrReport(
            @RequestBody ProgrammeAtrReportDto dto,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        if (dto.getProgramme() != null && dto.getBatch() != null) {
            reportAccessService.validateProgrammeAtrAccess(user, dto.getProgramme().getId(), dto.getBatch().getId());
        }

        return ResponseEntity.ok(ApiResponse.<ProgrammeAtrReportDto>builder()
                .success(true)
                .message("MasterProgramme ATR saved successfully")
                .data(atrService.saveProgrammeAtrReport(dto))
                .build());
    }

    @PostMapping("/programme-atr/{masterProgrammeId}/batch/{programmeBatchId}/submit")
    public ResponseEntity<ApiResponse<ProgrammeAtr>> submitProgrammeAtr(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateProgrammeAtrAccess(user, masterProgrammeId, programmeBatchId);

        String submitter = user != null ? user.getName() : "MasterProgramme Coordinator";
        return ResponseEntity.ok(ApiResponse.<ProgrammeAtr>builder()
                .success(true)
                .message("MasterProgramme ATR submitted for verification")
                .data(atrService.submitProgrammeAtr(masterProgrammeId, programmeBatchId, submitter))
                .build());
    }

    @GetMapping("/programme-atrs")
    public ResponseEntity<ApiResponse<List<ProgrammeAtrReportDto>>> listProgrammeAtrs(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        if (user != null && user.getRole() == UserRole.FACULTY) {
            return ResponseEntity.ok(ApiResponse.<List<ProgrammeAtrReportDto>>builder().success(true).data(Collections.emptyList()).build());
        }

        List<ProgrammeBatch> batches;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            batches = programmeBatchRepository.findById(programmeBatchId).map(List::of).orElse(Collections.emptyList());
        } else if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            batches = programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);
        } else {
            batches = programmeBatchRepository.findAll();
        }

        List<ProgrammeAtrReportDto> reports = batches.stream()
                .filter(b -> {
                    try {
                        reportAccessService.validateProgrammeAtrAccess(user, b.getMasterProgrammeId(), b.getId());
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(b -> atrService.getProgrammeAtrReport(b.getMasterProgrammeId(), b.getId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<ProgrammeAtrReportDto>>builder()
                .success(true)
                .data(reports)
                .build());
    }

    // --- Historical & ProgrammeBatch Summary Endpoints ---

    @GetMapping("/master-programmes/{masterProgrammeId}/batch-comparison")
    public ResponseEntity<ApiResponse<BatchComparisonDto>> getProgrammeBatchComparison(
            @PathVariable String masterProgrammeId,
            @RequestParam(required = false) List<String> programmeBatchIds,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateProgrammeAccess(user, masterProgrammeId);

        return ResponseEntity.ok(ApiResponse.<BatchComparisonDto>builder()
                .success(true)
                .data(atrService.getProgrammeBatchComparison(masterProgrammeId, programmeBatchIds))
                .build());
    }

    @GetMapping("/batch/{programmeBatchId}/summary")
    public ResponseEntity<ApiResponse<BatchContextDto>> getBatchSummary(
            @PathVariable String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateBatchAccess(user, programmeBatchId);

        return ResponseEntity.ok(ApiResponse.<BatchContextDto>builder()
                .success(true)
                .data(academicService.getBatchContext(programmeBatchId))
                .build());
    }

    // --- Attainment Main High-level Report ---

    @GetMapping("/attainment-main")
    public ResponseEntity<ApiResponse<ProgrammeAttainmentDatasetDto>> getAttainmentMainReport(
            @RequestParam String masterProgrammeId,
            @RequestParam String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateProgrammeAccess(user, masterProgrammeId);
        reportAccessService.validateBatchAccess(user, programmeBatchId);

        return ResponseEntity.ok(ApiResponse.<ProgrammeAttainmentDatasetDto>builder()
                .success(true)
                .data(attainmentCalculationService.getProgrammeAttainmentDataset(masterProgrammeId, programmeBatchId))
                .build());
    }

    @GetMapping("/attainment-main/course/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCourseAttainmentMainDetail(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(programmeBatchCourseId)
                .orElseThrow(() -> new com.dypiu.nba.exception.ResourceNotFoundException("MasterCourse Offering not found: " + programmeBatchCourseId));

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(attainmentCalculationService.calculateCourseCoAttainment(offering.getId()))
                .build());
    }

    // --- Phase 10: Persisted Course Attainment Report Endpoints ---

    @GetMapping("/course-attainment/{programmeBatchCourseId}")
    public ResponseEntity<ApiResponse<CourseAttainmentReportDto>> getCourseAttainmentReport(
            @PathVariable String programmeBatchCourseId) {
        return ResponseEntity.ok(ApiResponse.<CourseAttainmentReportDto>builder()
                .success(true)
                .data(attainmentReportService.getOrCreateCourseAttainmentReport(programmeBatchCourseId))
                .build());
    }

    @PostMapping("/course-attainment/{programmeBatchCourseId}/finalize")
    public ResponseEntity<ApiResponse<CourseAttainmentReportDto>> finalizeCourseAttainmentReport(
            @PathVariable String programmeBatchCourseId,
            Principal principal) {
        String actorName = principal != null ? principal.getName() : "Course Coordinator";
        return ResponseEntity.ok(ApiResponse.<CourseAttainmentReportDto>builder()
                .success(true)
                .message("Course Attainment Report finalized successfully")
                .data(attainmentReportService.finalizeCourseReport(programmeBatchCourseId, actorName))
                .build());
    }

    // --- Phase 10: Persisted Programme Attainment Report Endpoints ---

    @GetMapping("/programme-attainment/{masterProgrammeId}/batch/{programmeBatchId}")
    public ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> getProgrammeBatchAttainmentReport(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId) {
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchAttainmentReportDto>builder()
                .success(true)
                .data(attainmentReportService.getOrCreateProgrammeAttainmentReport(masterProgrammeId, programmeBatchId))
                .build());
    }

    @PostMapping("/programme-attainment/{masterProgrammeId}/batch/{programmeBatchId}/finalize")
    public ResponseEntity<ApiResponse<ProgrammeBatchAttainmentReportDto>> finalizeProgrammeBatchAttainmentReport(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId,
            Principal principal) {
        String actorName = principal != null ? principal.getName() : "Programme Coordinator";
        return ResponseEntity.ok(ApiResponse.<ProgrammeBatchAttainmentReportDto>builder()
                .success(true)
                .message("Programme Attainment Report finalized successfully")
                .data(attainmentReportService.finalizeProgrammeReport(masterProgrammeId, programmeBatchId, actorName))
                .build());
    }

    // --- Phase 10: Historical Report Discovery Endpoints ---

    @GetMapping("/historical/courses/{masterCourseId}/attainment")
    public ResponseEntity<ApiResponse<List<CourseAttainmentReportDto>>> getHistoricalCourseAttainmentReports(
            @PathVariable String masterCourseId) {
        return ResponseEntity.ok(ApiResponse.<List<CourseAttainmentReportDto>>builder()
                .success(true)
                .data(attainmentReportService.getHistoricalCourseAttainmentReports(masterCourseId))
                .build());
    }

    @GetMapping("/historical/programmes/{masterProgrammeId}/attainment")
    public ResponseEntity<ApiResponse<List<ProgrammeBatchAttainmentReportDto>>> getHistoricalProgrammeAttainmentReports(
            @PathVariable String masterProgrammeId) {
        return ResponseEntity.ok(ApiResponse.<List<ProgrammeBatchAttainmentReportDto>>builder()
                .success(true)
                .data(attainmentReportService.getHistoricalProgrammeAttainmentReports(masterProgrammeId))
                .build());
    }

    // --- Summary Endpoint ---
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReportsSummary(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            Principal principal) {
        String pId = (masterProgrammeId != null && !masterProgrammeId.isBlank()) ? masterProgrammeId.trim() : null;
        String bId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId.trim() : null;
        String cId = (masterCourseId != null && !masterCourseId.isBlank()) ? masterCourseId.trim() : null;

        Map<String, Object> courseAttainment = null;
        if (cId != null) {
            Map<String, Object> coAtt = attainmentCalculationService.calculateCourseCoAttainment(cId);
            com.dypiu.nba.dto.CourseMappingMatrixDto matrixDto = outcomeService.getCourseMappings(cId);
            courseAttainment = new LinkedHashMap<>();
            courseAttainment.put("masterCourseId", cId);
            courseAttainment.put("programmeBatchId", bId);
            courseAttainment.put("directAttainment", coAtt != null ? coAtt.get("directAttainment") : null);
            courseAttainment.put("indirectAttainment", coAtt != null ? coAtt.get("indirectAttainment") : null);
            courseAttainment.put("overallAttainment", coAtt != null ? coAtt.get("overallCoAttainment") : null);
            courseAttainment.put("coList", coAtt != null ? coAtt.get("coAttainments") : Collections.emptyList());
            courseAttainment.put("matrix", matrixDto != null ? matrixDto.getMatrix() : Collections.emptyMap());
        }

        Map<String, Object> programmeAttainment = null;
        if (pId != null && bId != null) {
            com.dypiu.nba.dto.ProgrammeAttainmentResultDto progAtt = attainmentCalculationService.calculateProgrammeAttainment(pId, bId);
            programmeAttainment = new LinkedHashMap<>();
            programmeAttainment.put("masterProgrammeId", pId);
            programmeAttainment.put("programmeBatchId", bId);
            programmeAttainment.put("poAttainment", progAtt != null ? progAtt.getPoAttainments() : Collections.emptyMap());
            programmeAttainment.put("psoAttainment", progAtt != null ? progAtt.getPsoAttainments() : Collections.emptyMap());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseAttainment", courseAttainment);
        data.put("programmeAttainment", programmeAttainment);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(data)
                .build());
    }

    // --- Generic Reports Export ---
    @GetMapping(value = {"/programme/{masterProgrammeId}/batch/{programmeBatchId}/export/excel", "/programme-batch/{programmeBatchId}/export/excel"}, produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportProgrammeBatchExcel(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            Principal principal) {
        User user = reportAccessService.getAuthenticatedUser(principal);
        String progId = masterProgrammeId;
        if (progId == null || progId.isBlank()) {
            ProgrammeBatch b = programmeBatchRepository.findById(programmeBatchId).orElse(null);
            if (b != null) progId = b.getMasterProgrammeId();
        }
        if (progId != null) {
            reportAccessService.validateProgrammeAccess(user, progId);
        }
        byte[] excelBytes = exportService.generateProgrammeBatchExcel(progId, programmeBatchId);
        String filename = "Programme_Attainment_Report_" + programmeBatchId + ".xlsx";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @GetMapping(value = "/export/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportReportsExcel(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) String reportType,
            Principal principal) {
        if ("PROGRAMME".equalsIgnoreCase(reportType) || (masterCourseId == null && programmeBatchId != null)) {
            return exportProgrammeBatchExcel(masterProgrammeId, programmeBatchId, principal);
        }
        if (masterCourseId == null || masterCourseId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "MasterCourse ID is required for course export.");
        }
        User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        String targetMasterCourseId = masterCourseId.trim();
        byte[] excelBytes = exportService.generateAttainmentExcel(targetMasterCourseId, programmeBatchId);
        String filename = "Attainment_Report_" + targetMasterCourseId + ".xlsx";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @GetMapping(value = "/export/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportReportsPdf(
            @RequestParam(required = false) String masterProgrammeId,
            @RequestParam(required = false) String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            @RequestParam(required = false) String reportType,
            Principal principal) {
        if (masterCourseId == null || masterCourseId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "MasterCourse ID is required for export.");
        }
        User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        String targetMasterCourseId = masterCourseId.trim();
        byte[] pdfBytes = exportService.generateAttainmentPdf(targetMasterCourseId, programmeBatchId);
        String filename = "Attainment_Report_" + targetMasterCourseId + ".pdf";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
