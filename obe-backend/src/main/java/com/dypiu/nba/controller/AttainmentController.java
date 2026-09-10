package com.dypiu.nba.controller;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.AttainmentConfiguration;
import com.dypiu.nba.service.AttainmentCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping({"/attainment", "/api/v1/attainment"})
@RequiredArgsConstructor
public class AttainmentController {

    private final AttainmentCalculationService calculationService;
    private final com.dypiu.nba.service.AttainmentReportService attainmentReportService;
    private final com.dypiu.nba.service.AttainmentReportExportService exportService;
    private final com.dypiu.nba.repository.UploadedDocumentRepository uploadedDocumentRepository;
    private final com.dypiu.nba.service.ReportAccessService reportAccessService;
    private final com.dypiu.nba.repository.ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final com.dypiu.nba.repository.ProgrammeBatchRepository programmeBatchRepository;

    @GetMapping({"/config/{masterCourseId}", "/configs/{masterCourseId}", "/configurations/programme-batch-courses/{masterCourseId}"})
    public ResponseEntity<ApiResponse<AttainmentConfiguration>> getConfig(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<AttainmentConfiguration>builder()
                .success(true)
                .data(calculationService.getAttainmentConfig(masterCourseId))
                .build());
    }

    @GetMapping({"/config/{masterCourseId}/approved", "/configs/{masterCourseId}/approved", "/configurations/programme-batch-courses/{masterCourseId}/approved"})
    public ResponseEntity<ApiResponse<AttainmentConfiguration>> getApprovedConfig(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<AttainmentConfiguration>builder()
                .success(true)
                .message("Approved attainment configuration retrieved successfully")
                .data(calculationService.getApprovedAttainmentConfig(masterCourseId))
                .build());
    }

    @RequestMapping(value = {"/config/{masterCourseId}", "/configs/{masterCourseId}", "/configurations/programme-batch-courses/{masterCourseId}"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<ApiResponse<AttainmentConfiguration>> saveConfig(
            @PathVariable String masterCourseId,
            @RequestBody AttainmentConfiguration config,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<AttainmentConfiguration>builder()
                .success(true)
                .message("Attainment configuration saved")
                .data(calculationService.saveAttainmentConfig(masterCourseId, config))
                .build());
    }

    @GetMapping({"/course/{masterCourseId}", "/courses/{masterCourseId}", "/calculate/course/{masterCourseId}", "/programme-batch-courses/{masterCourseId}"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCourseCoAttainment(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .message("CO Attainment calculated successfully")
                .data(calculationService.calculateCourseCoAttainment(masterCourseId))
                .build());
    }

    @GetMapping({"/courses/{masterCourseId}/direct", "/course/{masterCourseId}/direct", "/programme-batch-courses/{masterCourseId}/direct"})
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> getDirectAttainment(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .data(calculationService.getExaminationAttainment(masterCourseId))
                .build());
    }

    @GetMapping({"/courses/{masterCourseId}/indirect", "/course/{masterCourseId}/indirect", "/programme-batch-courses/{masterCourseId}/indirect"})
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> getIndirectAttainment(
            @PathVariable String masterCourseId,
            @RequestParam(required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .data(calculationService.getSurveyAttainment(masterCourseId))
                .build());
    }

    @PostMapping({"/assessment/direct/upload", "/assessment/internal/upload"})
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> uploadAssessmentDirect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "masterCourseId", required = false) String masterCourseId,
            @RequestParam(value = "programmeBatchCourseId", required = false) String programmeBatchCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            @RequestParam(value = "assessmentType", required = false) String assessmentType,
            @RequestParam(value = "toolType", required = false) String toolType,
            java.security.Principal principal) {
        return uploadAssessmentDirect(file, masterCourseId, programmeBatchCourseId, programmeBatchId, null, assessmentType, toolType, principal);
    }

    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> uploadAssessmentDirect(
            MultipartFile file,
            String masterCourseId,
            String programmeBatchCourseId,
            String programmeBatchId,
            BigDecimal thresholdPercentage,
            String assessmentType,
            String toolType,
            java.security.Principal principal) {
        String targetId = programmeBatchCourseId != null && !programmeBatchCourseId.isBlank() ? programmeBatchCourseId : masterCourseId;
        if (targetId == null || targetId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Course or Course Offering ID is required.");
        }
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(targetId)) {
            reportAccessService.validateCourseOfferingAccess(user, targetId);
        } else {
            reportAccessService.validateCourseAccess(user, targetId);
        }
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Assessment marks uploaded and processed successfully.")
                .data(calculationService.processAndSaveExaminationFile(targetId, file, thresholdPercentage, user != null ? user.getName() : "Course Coordinator"))
                .build());
    }

    @PostMapping({"/assessment/indirect/upload", "/survey/upload"})
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> uploadAssessmentIndirect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "masterCourseId", required = false) String masterCourseId,
            @RequestParam(value = "programmeBatchCourseId", required = false) String programmeBatchCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            java.security.Principal principal) {
        return uploadAssessmentIndirect(file, masterCourseId, programmeBatchCourseId, programmeBatchId, null, principal);
    }

    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> uploadAssessmentIndirect(
            MultipartFile file,
            String masterCourseId,
            String programmeBatchCourseId,
            String programmeBatchId,
            BigDecimal thresholdPercentage,
            java.security.Principal principal) {
        String targetId = programmeBatchCourseId != null && !programmeBatchCourseId.isBlank() ? programmeBatchCourseId : masterCourseId;
        if (targetId == null || targetId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Course or Course Offering ID is required.");
        }
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(targetId)) {
            reportAccessService.validateCourseOfferingAccess(user, targetId);
        } else {
            reportAccessService.validateCourseAccess(user, targetId);
        }
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Indirect survey responses uploaded and processed successfully.")
                .data(calculationService.processAndSaveSurveyFile(targetId, file, thresholdPercentage, user != null ? user.getName() : "Course Coordinator"))
                .build());
    }

    private String resolveProgrammeId(String masterProgrammeId, String programmeBatchId) {
        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            return masterProgrammeId;
        }
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            return programmeBatchRepository.findById(programmeBatchId)
                    .map(com.dypiu.nba.entity.ProgrammeBatch::getMasterProgrammeId)
                    .orElse(null);
        }
        return null;
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}", "/programme-batch/{programmeBatchId}", "/batch/{programmeBatchId}"})
    public ResponseEntity<ApiResponse<ProgrammeAttainmentResultDto>> getProgrammeAttainment(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        return ResponseEntity.ok(ApiResponse.<ProgrammeAttainmentResultDto>builder()
                .success(true)
                .message("Programme attainment calculated successfully")
                .data(calculationService.calculateProgrammeAttainment(progId, programmeBatchId))
                .build());
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}/dataset", "/programme-batch/{programmeBatchId}/dataset", "/batch/{programmeBatchId}/dataset"})
    public ResponseEntity<ApiResponse<ProgrammeAttainmentDatasetDto>> getProgrammeAttainmentDataset(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        return ResponseEntity.ok(ApiResponse.<ProgrammeAttainmentDatasetDto>builder()
                .success(true)
                .message("Programme attainment dataset retrieved successfully")
                .data(calculationService.getProgrammeAttainmentDataset(progId, programmeBatchId))
                .build());
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}/average-mapping", "/programme-batch/{programmeBatchId}/average-mapping", "/batch/{programmeBatchId}/average-mapping"})
    public ResponseEntity<ApiResponse<Object>> getProgrammeAverageMapping(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(progId, programmeBatchId);

        Map<String, BigDecimal> avgMappingStrength = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        if (report.getReport1AverageMappingPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PoRow r : report.getReport1AverageMappingPO()) {
                if (r.getPoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPoCode().toUpperCase(), r.getProgrammeAverageMapping());
                    sum = sum.add(r.getProgrammeAverageMapping());
                    count++;
                }
            }
        }
        if (report.getReport1AverageMappingPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PsoRow r : report.getReport1AverageMappingPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPsoCode().toUpperCase(), r.getProgrammeAverageMapping());
                    sum = sum.add(r.getProgrammeAverageMapping());
                    count++;
                }
            }
        }
        BigDecimal overallAvg = count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageMappingStrength", avgMappingStrength);
        result.put("overallAverageMappingStrength", overallAvg);
        result.put("poMappings", report.getReport1AverageMappingPO() != null ? report.getReport1AverageMappingPO() : Collections.emptyList());
        result.put("psoMappings", report.getReport1AverageMappingPSO() != null ? report.getReport1AverageMappingPSO() : Collections.emptyList());
        result.put("courses", report.getCourseMappingRows() != null ? report.getCourseMappingRows() : Collections.emptyList());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}/average-direct", "/programme-batch/{programmeBatchId}/average-direct", "/batch/{programmeBatchId}/average-direct"})
    public ResponseEntity<ApiResponse<Object>> getProgrammeAverageDirect(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(progId, programmeBatchId);

        Map<String, BigDecimal> avgDirectAttainment = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        if (report.getReport2DirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PoRow r : report.getReport2DirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                    sum = sum.add(r.getProgrammeDirectAttainment());
                    count++;
                }
            }
        }
        if (report.getReport2DirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PsoRow r : report.getReport2DirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPsoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                    sum = sum.add(r.getProgrammeDirectAttainment());
                    count++;
                }
            }
        }
        BigDecimal overallDirect = count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageDirectAttainment", avgDirectAttainment);
        result.put("overallDirectAttainment", overallDirect);
        result.put("poDirectAttainment", report.getReport2DirectAttainmentPO() != null ? report.getReport2DirectAttainmentPO() : Collections.emptyList());
        result.put("psoDirectAttainment", report.getReport2DirectAttainmentPSO() != null ? report.getReport2DirectAttainmentPSO() : Collections.emptyList());
        result.put("courses", report.getCourseDirectAttainmentRows() != null ? report.getCourseDirectAttainmentRows() : Collections.emptyList());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}/average-indirect", "/programme-batch/{programmeBatchId}/average-indirect", "/batch/{programmeBatchId}/average-indirect"})
    public ResponseEntity<ApiResponse<Object>> getProgrammeAverageIndirect(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(progId, programmeBatchId);

        Map<String, BigDecimal> avgIndirectAttainment = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        if (report.getReport3IndirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PoRow r : report.getReport3IndirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                    sum = sum.add(r.getIndirectAttainmentLevel());
                    count++;
                }
            }
        }
        if (report.getReport3IndirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PsoRow r : report.getReport3IndirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPsoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                    sum = sum.add(r.getIndirectAttainmentLevel());
                    count++;
                }
            }
        }
        BigDecimal overallIndirect = count > 0 ? sum.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageIndirectAttainment", avgIndirectAttainment);
        result.put("overallIndirectAttainment", overallIndirect);
        result.put("studentResponses", report.getStudentSurveyRows() != null ? report.getStudentSurveyRows() : Collections.emptyList());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping({"/programme/{masterProgrammeId}/batch/{programmeBatchId}/overall", "/programme-batch/{programmeBatchId}/overall", "/batch/{programmeBatchId}/overall"})
    public ResponseEntity<ApiResponse<Object>> getProgrammeOverall(
            @PathVariable(required = false) String masterProgrammeId,
            @PathVariable String programmeBatchId,
            java.security.Principal principal) {
        String progId = resolveProgrammeId(masterProgrammeId, programmeBatchId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (progId != null) {
            reportAccessService.validateProgrammeAtrAccess(user, progId, programmeBatchId);
        }
        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(progId, programmeBatchId);

        Map<String, BigDecimal> avgMappingStrength = new LinkedHashMap<>();
        if (report.getReport1AverageMappingPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PoRow r : report.getReport1AverageMappingPO()) {
                if (r.getPoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPoCode().toUpperCase(), r.getProgrammeAverageMapping());
                }
            }
        }
        if (report.getReport1AverageMappingPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PsoRow r : report.getReport1AverageMappingPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPsoCode().toUpperCase(), r.getProgrammeAverageMapping());
                }
            }
        }

        Map<String, BigDecimal> avgDirectAttainment = new LinkedHashMap<>();
        if (report.getReport2DirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PoRow r : report.getReport2DirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                }
            }
        }
        if (report.getReport2DirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PsoRow r : report.getReport2DirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPsoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                }
            }
        }

        Map<String, BigDecimal> avgIndirectAttainment = new LinkedHashMap<>();
        if (report.getReport3IndirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PoRow r : report.getReport3IndirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                }
            }
        }
        if (report.getReport3IndirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PsoRow r : report.getReport3IndirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPsoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                }
            }
        }

        Map<String, BigDecimal> finalAttainments = new LinkedHashMap<>();
        if (report.getReport4OverallAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report4PoRow r : report.getReport4OverallAttainmentPO()) {
                if (r.getPoCode() != null && r.getFinalAttainment() != null) {
                    finalAttainments.put(r.getPoCode().toUpperCase(), r.getFinalAttainment());
                }
            }
        }
        if (report.getReport4OverallAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report4PsoRow r : report.getReport4OverallAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getFinalAttainment() != null) {
                    finalAttainments.put(r.getPsoCode().toUpperCase(), r.getFinalAttainment());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageMappingStrength", avgMappingStrength);
        result.put("averageDirectAttainment", avgDirectAttainment);
        result.put("averageIndirectAttainment", avgIndirectAttainment);
        result.put("finalAttainments", finalAttainments);
        result.put("overallProgrammeAttainment", report.getOverallProgrammeAttainment());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .data(result)
                .build());
    }

    @PostMapping(value = "/programmes/{masterProgrammeId}/batches/{programmeBatchId}/programme-survey/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProgrammeSurveyResultDto>> uploadProgrammeSurvey(
            @PathVariable String masterProgrammeId,
            @PathVariable String programmeBatchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateProgrammeAtrAccess(user, masterProgrammeId, programmeBatchId);
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Programme Coordinator");
        return ResponseEntity.ok(ApiResponse.<ProgrammeSurveyResultDto>builder()
                .success(true)
                .message("Programme exit survey processed successfully")
                .data(calculationService.processAndSaveProgrammeSurveyFile(masterProgrammeId, programmeBatchId, file, uploader))
                .build());
    }


    // --- Examination Attainment Endpoints (Sheet 2: Examination) ---

    @GetMapping({"/examination/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/examination"})
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> getExaminationAttainment(
            @PathVariable String programmeBatchCourseId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Examination attainment fetched successfully")
                .data(calculationService.getExaminationAttainment(programmeBatchCourseId))
                .build());
    }

    @PostMapping({"/examination/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/examination"})
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> saveAndCalculateExaminationAttainment(
            @PathVariable String programmeBatchCourseId,
            @RequestBody ExaminationMarksPayloadDto payload,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Examination threshold, out-of marks, student marks saved and attainment calculated successfully")
                .data(calculationService.calculateExaminationAttainment(programmeBatchCourseId, payload))
                .build());
    }

    @PostMapping(value = {"/examination/{programmeBatchCourseId}/upload", "/course-offerings/{programmeBatchCourseId}/examination/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExaminationAttainmentResultDto>> uploadAndProcessExaminationSheet(
            @PathVariable String programmeBatchCourseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thresholdPercentage", required = false) BigDecimal thresholdPercentage,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Course Coordinator");
        return ResponseEntity.ok(ApiResponse.<ExaminationAttainmentResultDto>builder()
                .success(true)
                .message("Examination sheet saved with audit metadata, parsed via POI, and attainment calculated successfully")
                .data(calculationService.processAndSaveExaminationFile(programmeBatchCourseId, file, thresholdPercentage, uploader))
                .build());
    }

    @DeleteMapping({"/examination/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/examination"})
    public ResponseEntity<ApiResponse<Void>> deleteExaminationData(
            @PathVariable String programmeBatchCourseId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        calculationService.deleteExaminationData(programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Examination marks and uploaded sheet deleted successfully")
                .build());
    }

    // --- Course CO Attainment Full Report (Table 1, Table 2, Attainment Table) ---
    @GetMapping({
            "/course/{programmeBatchCourseId}",
            "/co-attainment/{programmeBatchCourseId}",
            "/course-offerings/{programmeBatchCourseId}/attainment",
            "/programme-batch-courses/{programmeBatchCourseId}/attainment-main",
            "/programme-batch-courses/{programmeBatchCourseId}/co-attainment",
            "/programme-batch-courses/{programmeBatchCourseId}/course-attainment"
    })
    public ResponseEntity<ApiResponse<CourseAttainmentReportDto>> getCourseCoAttainmentReport(
            @PathVariable String programmeBatchCourseId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<CourseAttainmentReportDto>builder()
                .success(true)
                .message("Course CO attainment report fetched successfully")
                .data(attainmentReportService.getOrCreateCourseAttainmentReport(programmeBatchCourseId))
                .build());
    }

    // --- Course End Survey Attainment Endpoints (Sheet 3: Course End Survey) ---

    @GetMapping({"/survey/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/survey", "/programme-batch-courses/{programmeBatchCourseId}/survey"})
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> getSurveyAttainment(
            @PathVariable String programmeBatchCourseId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey attainment fetched successfully")
                .data(calculationService.getSurveyAttainment(programmeBatchCourseId))
                .build());
    }

    @PostMapping({"/survey/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/survey", "/programme-batch-courses/{programmeBatchCourseId}/survey"})
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> saveAndCalculateSurveyAttainment(
            @PathVariable String programmeBatchCourseId,
            @RequestBody SurveyMarksPayloadDto payload,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey responses saved and indirect attainment calculated successfully")
                .data(calculationService.calculateSurveyAttainment(programmeBatchCourseId, payload))
                .build());
    }

    @PostMapping(value = {"/survey/{programmeBatchCourseId}/upload", "/course-offerings/{programmeBatchCourseId}/survey/upload", "/programme-batch-courses/{programmeBatchCourseId}/survey/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SurveyAttainmentResultDto>> uploadAndProcessSurveySheet(
            @PathVariable String programmeBatchCourseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thresholdPercentage", required = false) BigDecimal thresholdPercentage,
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        String uploader = (uploadedBy != null && !uploadedBy.isBlank()) ? uploadedBy : (principal != null ? principal.getName() : "Course Coordinator");
        return ResponseEntity.ok(ApiResponse.<SurveyAttainmentResultDto>builder()
                .success(true)
                .message("Course End Survey sheet saved with audit metadata, parsed via POI, and indirect attainment calculated successfully")
                .data(calculationService.processAndSaveSurveyFile(programmeBatchCourseId, file, thresholdPercentage, uploader))
                .build());
    }

    @DeleteMapping({"/survey/{programmeBatchCourseId}", "/course-offerings/{programmeBatchCourseId}/survey", "/programme-batch-courses/{programmeBatchCourseId}/survey"})
    public ResponseEntity<ApiResponse<Void>> deleteSurveyData(
            @PathVariable String programmeBatchCourseId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, programmeBatchCourseId);
        calculationService.deleteSurveyData(programmeBatchCourseId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Course End Survey responses and uploaded sheet deleted successfully")
                .build());
    }

    @GetMapping("/documents/{courseOfferingOrMasterCourseId}")
    public ResponseEntity<ApiResponse<java.util.List<com.dypiu.nba.entity.UploadedDocument>>> getUploadedDocuments(
            @PathVariable String courseOfferingOrMasterCourseId,
            java.security.Principal principal) {
        String offeringId = calculationService.resolveOfferingId(courseOfferingOrMasterCourseId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, offeringId);
        return ResponseEntity.ok(ApiResponse.<java.util.List<com.dypiu.nba.entity.UploadedDocument>>builder()
                .success(true)
                .message("Uploaded audit documents retrieved successfully")
                .data(calculationService.getUploadedDocumentsForCourse(courseOfferingOrMasterCourseId))
                .build());
    }

    @GetMapping("/documents/{masterCourseId}/download/{documentType}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadUploadedDocument(
            @PathVariable String masterCourseId,
            @PathVariable String documentType,
            java.security.Principal principal) {
        String offeringId = calculationService.resolveOfferingId(masterCourseId);
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        reportAccessService.validateCourseOfferingAccess(user, offeringId);

        com.dypiu.nba.entity.DocumentType dt = null;
        try {
            dt = com.dypiu.nba.entity.DocumentType.valueOf(documentType.toUpperCase());
        } catch (Exception ignored) {}

        com.dypiu.nba.entity.UploadedDocument doc = (dt != null)
                ? uploadedDocumentRepository.findFirstByProgrammeBatchCourseIdAndDocumentTypeOrderByUploadedAtDesc(offeringId, dt).orElse(null)
                : null;

        if (doc == null || doc.getSavedPath() == null) {
            return ResponseEntity.notFound().build();
        }

        java.io.File file = new java.io.File(doc.getSavedPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
        String fileName = doc.getFileName() != null ? doc.getFileName() : file.getName();

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }


    // =========================================================================
    //  ATTAINMENT EXPORT ENDPOINTS (EXCEL & PDF)
    // =========================================================================

    @GetMapping(value = "/export/excel/{masterCourseId}", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportAttainmentExcel(
            @PathVariable String masterCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        byte[] excelBytes = exportService.generateAttainmentExcel(masterCourseId, programmeBatchId);
        String filename = "Attainment_Sheet_" + masterCourseId + ".xlsx";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @GetMapping(value = "/export/pdf/{masterCourseId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportAttainmentPdf(
            @PathVariable String masterCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            java.security.Principal principal) {
        com.dypiu.nba.entity.User user = reportAccessService.getAuthenticatedUser(principal);
        if (programmeBatchCourseRepository.existsById(masterCourseId)) {
            reportAccessService.validateCourseOfferingAccess(user, masterCourseId);
        } else {
            reportAccessService.validateCourseAccess(user, masterCourseId);
        }
        byte[] pdfBytes = exportService.generateAttainmentPdf(masterCourseId, programmeBatchId);
        String filename = "NBA_Attainment_Report_" + masterCourseId + ".pdf";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping(value = "/reports/{masterCourseId}/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportAttainmentExcelAlias(
            @PathVariable String masterCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            java.security.Principal principal) {
        return exportAttainmentExcel(masterCourseId, programmeBatchId, principal);
    }

    @GetMapping(value = "/reports/{masterCourseId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportAttainmentPdfAlias(
            @PathVariable String masterCourseId,
            @RequestParam(value = "programmeBatchId", required = false) String programmeBatchId,
            java.security.Principal principal) {
        return exportAttainmentPdf(masterCourseId, programmeBatchId, principal);
    }
}
