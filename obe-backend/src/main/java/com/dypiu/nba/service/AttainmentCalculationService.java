package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.exception.BadRequestException;
import com.dypiu.nba.exception.ResourceNotFoundException;
import com.dypiu.nba.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttainmentCalculationService {

    private final AttainmentConfigurationRepository configRepository;
    private final StudentCoMarkRepository studentCoMarkRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final StudentRepository studentRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeOutcomeRepository programmeOutcomeRepository;
    private final ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;
    private final CoPoMappingRepository coPoMappingRepository;
    private final CoPsoMappingRepository coPsoMappingRepository;
    private final com.dypiu.nba.security.CurrentUserScopeService currentUserScopeService;
    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final AuditLogService auditLogService;
    private final MappingService mappingService;
    private final BatchLifecycleService batchLifecycleService;
    private final IndirectAssessmentService indirectAssessmentService;

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir:${app.upload.dir:${UPLOAD_STORAGE_PATH:${APP_UPLOAD_DIR:/app/uploads}}}}")
    private String baseUploadDir;

    private final Map<String, ExaminationAttainmentResultDto> examinationAttainmentStore = new ConcurrentHashMap<>();
    private final Map<String, SurveyAttainmentResultDto> surveyAttainmentStore = new ConcurrentHashMap<>();
    private final Map<String, ProgrammeSurveyResultDto> programmeSurveyStore = new ConcurrentHashMap<>();

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file.xlsx";
        }
        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void validateUploadedSpreadsheet(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Uploaded file must have a valid filename");
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            throw new BadRequestException("Invalid file extension. Only .xlsx, .xls, and .csv files are permitted.");
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read >= 2) {
                // ZIP / OOXML (.xlsx) header: 0x50 0x4B (PK)
                boolean isZip = header[0] == 0x50 && header[1] == 0x4B;
                // OLE2 (.xls) header: 0xD0 0xCF 0x11 0xE0
                boolean isOle2 = read >= 4 && (header[0] & 0xFF) == 0xD0 && (header[1] & 0xFF) == 0xCF
                        && (header[2] & 0xFF) == 0x11 && (header[3] & 0xFF) == 0xE0;
                boolean isCsv = lower.endsWith(".csv");
                if (!isZip && !isOle2 && !isCsv) {
                    throw new BadRequestException("Invalid file content: file header does not match allowed spreadsheet format.");
                }
            }
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.warn("[AttainmentCalculationService] Spreadsheet header inspection skipped: {}", e.getMessage());
        }
    }

    public Path saveUploadedFile(MultipartFile file, String subCategory, String programmeBatchCourseId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validateUploadedSpreadsheet(file);
        try {
            String base = (baseUploadDir != null && !baseUploadDir.isBlank()) ? baseUploadDir : "/app/uploads";
            Path targetDirectory = Paths.get(base, subCategory, programmeBatchCourseId).toAbsolutePath().normalize();
            Files.createDirectories(targetDirectory);

            String originalFilename = file.getOriginalFilename();
            String safeFileName = System.currentTimeMillis() + "_" + sanitizeFilename(originalFilename);
            Path targetFilePath = targetDirectory.resolve(safeFileName).normalize();

            if (!targetFilePath.startsWith(targetDirectory)) {
                throw new BadRequestException("Invalid file path: path traversal detected");
            }

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[AttainmentCalculationService] Successfully stored uploaded file: {}", targetFilePath);
            return targetFilePath;
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("[AttainmentCalculationService] Failed to store uploaded file: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    public Path saveProgrammeSurveyUploadedFile(MultipartFile file, String masterProgrammeId, String programmeBatchId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validateUploadedSpreadsheet(file);
        try {
            String base = (baseUploadDir != null && !baseUploadDir.isBlank()) ? baseUploadDir : "/app/uploads";
            Path targetDirectory = Paths.get(base, "programme-survey", masterProgrammeId, programmeBatchId).toAbsolutePath().normalize();
            Files.createDirectories(targetDirectory);

            String originalFilename = file.getOriginalFilename();
            String safeFileName = System.currentTimeMillis() + "_" + sanitizeFilename(originalFilename);
            Path targetFilePath = targetDirectory.resolve(safeFileName).normalize();

            if (!targetFilePath.startsWith(targetDirectory)) {
                throw new BadRequestException("Invalid file path: path traversal detected");
            }

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[AttainmentCalculationService] Successfully stored programme survey file: {}", targetFilePath);
            return targetFilePath;
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            log.error("[AttainmentCalculationService] Failed to store programme survey file: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store programme survey file: " + e.getMessage(), e);
        }
    }

    public String resolveOfferingId(String offeringOrMasterCourseId) {
        if (offeringOrMasterCourseId == null || offeringOrMasterCourseId.isBlank()) return null;
        if (programmeBatchCourseRepository.existsById(offeringOrMasterCourseId)) {
            return offeringOrMasterCourseId;
        }
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByCode(offeringOrMasterCourseId);
        if (!offerings.isEmpty()) {
            return offerings.get(0).getId();
        }
        return offeringOrMasterCourseId;
    }

    private CurrentUserScope getScope() {
        try {
            return currentUserScopeService != null ? currentUserScopeService.getCurrentUserScope() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceOfferingEditability(String courseOfferingOrMasterCourseId) {
        if (courseOfferingOrMasterCourseId == null || courseOfferingOrMasterCourseId.isBlank()) return;
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        if (offeringId != null) {
            ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
            if (offering != null && offering.getProgrammeBatchId() != null) {
                batchLifecycleService.enforceBatchEditability(offering.getProgrammeBatchId());
            }
        }
    }

    public boolean isCourseCoordinatorAssigned(ProgrammeBatchCourse offering, Long userId, String userName, String userEmail) {
        if (offering == null) return false;

        // 1. programmeBatchCourse.courseCoordinatorId equals authenticatedUser.id
        boolean idMatch = offering.getCourseCoordinatorId() != null && userId != null
                && Objects.equals(offering.getCourseCoordinatorId(), userId);

        // 2a. programmeBatchCourse.courseCoordinatorEmail equals authenticatedUser.email, case-insensitive (independent)
        String ccEmail = offering.getCourseCoordinatorEmail();
        boolean ccEmailMatch = ccEmail != null && !ccEmail.isBlank() && userEmail != null && !userEmail.isBlank()
                && ccEmail.trim().equalsIgnoreCase(userEmail.trim());

        // 2b. programmeBatchCourse.coordinatorEmail equals authenticatedUser.email, case-insensitive (independent)
        String cEmail = offering.getCoordinatorEmail();
        boolean cEmailMatch = cEmail != null && !cEmail.isBlank() && userEmail != null && !userEmail.isBlank()
                && cEmail.trim().equalsIgnoreCase(userEmail.trim());

        // 3. programmeBatchCourse.courseCoordinatorName equals authenticatedUser.name, case-insensitive
        String ccName = offering.getCourseCoordinatorName();
        boolean nameMatch = ccName != null && !ccName.isBlank() && userName != null && !userName.isBlank()
                && ccName.trim().equalsIgnoreCase(userName.trim());

        // 4. If legacy records store an email inside courseCoordinatorName, compare it with authenticatedUser.email, case-insensitive
        boolean nameEmailMatch = ccName != null && !ccName.isBlank() && userEmail != null && !userEmail.isBlank()
                && ccName.trim().equalsIgnoreCase(userEmail.trim());

        // 5. Assigned faculty fallback when coordinator ID/name are unassigned
        boolean assignedFacultyFallback = false;
        if (offering.getCourseCoordinatorId() == null && (ccName == null || ccName.isBlank())) {
            String assignedFaculty = offering.getAssignedFaculty();
            if (assignedFaculty != null && !assignedFaculty.isBlank()) {
                if (userEmail != null && !userEmail.isBlank() && assignedFaculty.toLowerCase().contains(userEmail.trim().toLowerCase())) {
                    assignedFacultyFallback = true;
                } else if (userName != null && !userName.isBlank() && assignedFaculty.toLowerCase().contains(userName.trim().toLowerCase())) {
                    assignedFacultyFallback = true;
                }
            }
        }

        return idMatch || ccEmailMatch || cEmailMatch || nameMatch || nameEmailMatch || assignedFacultyFallback;
    }

    private void enforceOfferingOrCourseScope(String courseOfferingOrMasterCourseId) {
        if (courseOfferingOrMasterCourseId == null || courseOfferingOrMasterCourseId.isBlank()) return;
        com.dypiu.nba.security.CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;

        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        if (offeringId != null && programmeBatchCourseRepository.existsById(offeringId)) {
            ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
            if (offering != null) {
                if (scope.isFaculty()) {
                    boolean isCoord = isCourseCoordinatorAssigned(offering, scope.getUserId(), scope.getName(), scope.getEmail());
                    if (!isCoord) {
                        log.info("Course coordinator authorization failed for resolved ProgrammeBatchCourse ID={}: authenticated JWT user [id={}, name={}, email={}], offering coordinator [courseCoordinatorId={}, courseCoordinatorName={}, courseCoordinatorEmail={}, coordinatorEmail={}, assignedFaculty={}]",
                                offering.getId(), scope.getUserId(), scope.getName(), scope.getEmail(),
                                offering.getCourseCoordinatorId(), offering.getCourseCoordinatorName(),
                                offering.getCourseCoordinatorEmail(), offering.getCoordinatorEmail(), offering.getAssignedFaculty());
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this Course Offering.");
                    }
                    return;
                }
                if (scope.isProgrammeCoordinator()) {
                    if (offering.getProgrammeBatchId() != null) {
                        ProgrammeBatch batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(offering.getProgrammeBatchId())
                                .orElseThrow(() -> new ResourceNotFoundException("Programme batch not found: " + offering.getProgrammeBatchId()));
                        boolean isAssigned = (scope.getUserId() != null && Objects.equals(batch.getCoordinatorId(), scope.getUserId()))
                                || (scope.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim()))
                                || (scope.getName() != null && batch.getCoordinatorName() != null && batch.getCoordinatorName().trim().equalsIgnoreCase(scope.getName().trim()));
                        if (!isAssigned) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not the assigned Programme Coordinator for this Programme Batch.");
                        }
                    } else {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course Offering is not associated with any Programme Batch.");
                    }
                    return;
                }
                return;
            }
        }
    }

    @Transactional(readOnly = true)
    public AttainmentConfiguration getAttainmentConfig(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] getAttainmentConfig called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        AttainmentConfiguration cfg = configRepository.findByProgrammeBatchCourseId(offeringId)
                .orElseGet(() -> AttainmentConfiguration.builder()
                        .id("cfg-" + offeringId)
                        .programmeBatchCourseId(offeringId)
                        .directWeight(new BigDecimal("80.00"))
                        .indirectWeight(new BigDecimal("20.00"))
                        .directThreshold(new BigDecimal("60.00"))
                        .indirectThreshold(new BigDecimal("60.00"))
                        .approvedDirectWeight(new BigDecimal("80.00"))
                        .approvedIndirectWeight(new BigDecimal("20.00"))
                        .approvedDirectThreshold(new BigDecimal("60.00"))
                        .approvedIndirectThreshold(new BigDecimal("60.00"))
                        .status(AttainmentConfigStatus.DRAFT)
                        .build());
        if (approvalRequestRepository != null) {
            approvalRequestRepository.findByProgrammeBatchCourseId(offeringId).stream()
                    .filter(a -> a.getType() == ApprovalType.ATTAINMENT_CONFIGURATION || a.getType() == ApprovalType.ATTAINMENT_SETTINGS)
                    .max(Comparator.comparing(ApprovalRequest::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .ifPresent(req -> {
                        cfg.setRevisionReason(req.getRemarks());
                        cfg.setReviewedBy(req.getApprovedBy());
                    });
        }
        return cfg;
    }

    @Transactional(readOnly = true)
    public AttainmentConfiguration getApprovedAttainmentConfig(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] getApprovedAttainmentConfig called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        AttainmentConfiguration cfg = configRepository.findByProgrammeBatchCourseId(offeringId)
                .orElseGet(() -> AttainmentConfiguration.builder()
                        .id("cfg-" + offeringId)
                        .programmeBatchCourseId(offeringId)
                        .directWeight(new BigDecimal("80.00"))
                        .indirectWeight(new BigDecimal("20.00"))
                        .directThreshold(new BigDecimal("60.00"))
                        .indirectThreshold(new BigDecimal("60.00"))
                        .approvedDirectWeight(new BigDecimal("80.00"))
                        .approvedIndirectWeight(new BigDecimal("20.00"))
                        .approvedDirectThreshold(new BigDecimal("60.00"))
                        .approvedIndirectThreshold(new BigDecimal("60.00"))
                        .status(AttainmentConfigStatus.DRAFT)
                        .build());

        AttainmentConfiguration approvedView = AttainmentConfiguration.builder()
                .id(cfg.getId())
                .programmeBatchCourseId(cfg.getProgrammeBatchCourseId())
                .directWeight(cfg.getEffectiveApprovedDirectWeight())
                .indirectWeight(cfg.getEffectiveApprovedIndirectWeight())
                .directThreshold(cfg.getEffectiveApprovedDirectThreshold())
                .indirectThreshold(cfg.getEffectiveApprovedIndirectThreshold())
                .directLevelsJson(cfg.getEffectiveApprovedDirectLevelsJson())
                .indirectLevelsJson(cfg.getEffectiveApprovedIndirectLevelsJson())
                .approvedDirectWeight(cfg.getEffectiveApprovedDirectWeight())
                .approvedIndirectWeight(cfg.getEffectiveApprovedIndirectWeight())
                .approvedDirectThreshold(cfg.getEffectiveApprovedDirectThreshold())
                .approvedIndirectThreshold(cfg.getEffectiveApprovedIndirectThreshold())
                .approvedDirectLevelsJson(cfg.getEffectiveApprovedDirectLevelsJson())
                .approvedIndirectLevelsJson(cfg.getEffectiveApprovedIndirectLevelsJson())
                .status(cfg.getStatus())
                .submittedBy(cfg.getSubmittedBy())
                .submittedAt(cfg.getSubmittedAt())
                .approvedBy(cfg.getApprovedBy())
                .approvedAt(cfg.getApprovedAt())
                .createdAt(cfg.getCreatedAt())
                .updatedAt(cfg.getUpdatedAt())
                .build();

        if (approvalRequestRepository != null) {
            approvalRequestRepository.findByProgrammeBatchCourseId(offeringId).stream()
                    .filter(a -> a.getType() == ApprovalType.ATTAINMENT_CONFIGURATION || a.getType() == ApprovalType.ATTAINMENT_SETTINGS)
                    .max(Comparator.comparing(ApprovalRequest::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .ifPresent(req -> {
                        approvedView.setRevisionReason(req.getRemarks());
                        approvedView.setReviewedBy(req.getApprovedBy());
                    });
        }
        return approvedView;
    }

    @Transactional
    public AttainmentConfiguration saveAttainmentConfig(String courseOfferingOrMasterCourseId, AttainmentConfiguration config) {
        log.debug("[AttainmentCalculationService] saveAttainmentConfig called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.ATTAINMENT_CONFIGURATION, offeringId, null);
        }

        AttainmentConfiguration existing = configRepository.findByProgrammeBatchCourseId(offeringId).orElse(null);
        if (existing != null) {
            existing.setDirectWeight(config.getDirectWeight() != null ? config.getDirectWeight() : new BigDecimal("80.00"));
            existing.setIndirectWeight(config.getIndirectWeight() != null ? config.getIndirectWeight() : new BigDecimal("20.00"));
            existing.setDirectThreshold(config.getDirectThreshold() != null ? config.getDirectThreshold() : new BigDecimal("60.00"));
            existing.setIndirectThreshold(config.getIndirectThreshold() != null ? config.getIndirectThreshold() : new BigDecimal("60.00"));
            if (config.getDirectLevelsJson() != null) {
                existing.setDirectLevelsJson(config.getDirectLevelsJson());
            }
            if (config.getIndirectLevelsJson() != null) {
                existing.setIndirectLevelsJson(config.getIndirectLevelsJson());
            }
            existing.setStatus(AttainmentConfigStatus.DRAFT);
            existing.setUpdatedAt(ZonedDateTime.now());
            return configRepository.save(existing);
        } else {
            config.setStatus(AttainmentConfigStatus.DRAFT);
            config.setProgrammeBatchCourseId(offeringId);
            if (config.getId() == null) config.setId("cfg-" + offeringId);
            if (config.getApprovedDirectWeight() == null) config.setApprovedDirectWeight(new BigDecimal("80.00"));
            if (config.getApprovedIndirectWeight() == null) config.setApprovedIndirectWeight(new BigDecimal("20.00"));
            if (config.getApprovedDirectThreshold() == null) config.setApprovedDirectThreshold(new BigDecimal("60.00"));
            if (config.getApprovedIndirectThreshold() == null) config.setApprovedIndirectThreshold(new BigDecimal("60.00"));
            return configRepository.save(config);
        }
    }

    // --- Database Persistence Helper Methods ---

    @Transactional
    public void saveStudentCoMarksToDatabase(String courseOfferingOrMasterCourseId, Map<String, BigDecimal> coMaxMarks, List<StudentMarksRowDto> studentList) {
        log.debug("[AttainmentCalculationService] saveStudentCoMarksToDatabase called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId + " | students: " + (studentList != null ? studentList.size() : 0));
        if (courseOfferingOrMasterCourseId == null || studentList == null || studentList.isEmpty()) return;

        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);

        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + offeringId));

        String programmeBatchId = offering.getProgrammeBatchId();

        // 1. Collect all valid PRNs from upload
        Set<String> prnSet = studentList.stream()
                .map(StudentMarksRowDto::getPrn)
                .filter(prn -> prn != null && !prn.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Bulk lookup existing students by PRN
        Map<String, Student> studentMap = prnSet.isEmpty()
                ? new HashMap<>()
                : studentRepository.findByPrnIn(prnSet).stream()
                        .filter(s -> s.getPrn() != null)
                        .collect(Collectors.toMap(s -> s.getPrn().trim(), Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<Student> studentsToSave = new ArrayList<>();
        Map<String, Student> effectiveStudentMap = new LinkedHashMap<>(studentMap);

        // Ensure all students in the upload exist; auto-create or update any missing batch id
        for (StudentMarksRowDto st : studentList) {
            String prn = st.getPrn();
            if (prn == null || prn.isBlank()) continue;
            String cleanPrn = prn.trim();

            Student student = effectiveStudentMap.get(cleanPrn);
            if (student == null) {
                Student newStudent = Student.builder()
                        .id("std-" + UUID.randomUUID().toString().substring(0, 8))
                        .prn(cleanPrn)
                        .name(st.getStudentName() != null && !st.getStudentName().isBlank() ? st.getStudentName().trim() : "Student " + cleanPrn)
                        .email(cleanPrn.toLowerCase() + "@dypiu.ac.in")
                        .programmeBatchId(programmeBatchId)
                        .status(StudentStatus.ENROLLED)
                        .build();
                effectiveStudentMap.put(cleanPrn, newStudent);
                studentsToSave.add(newStudent);
            } else if (student.getProgrammeBatchId() == null) {
                student.setProgrammeBatchId(programmeBatchId);
                studentsToSave.add(student);
            }
        }

        if (!studentsToSave.isEmpty()) {
            studentRepository.saveAll(studentsToSave);
        }

        // 2. Delete existing marks for this offering
        studentCoMarkRepository.deleteByProgrammeBatchCourseId(offeringId);
        studentCoMarkRepository.flush();

        // 3. Save Student CO marks
        List<StudentCoMark> markEntities = new ArrayList<>();
        for (StudentMarksRowDto st : studentList) {
            String prn = st.getPrn();
            if (prn == null || prn.isBlank()) continue;
            String cleanPrn = prn.trim();
            Student student = effectiveStudentMap.get(cleanPrn);
            String studentId = student != null ? student.getId() : ("std-" + cleanPrn);
            String studentName = student != null ? student.getName() : (st.getStudentName() != null ? st.getStudentName() : "Student " + cleanPrn);

            if (st.getCoMarks() != null) {
                for (Map.Entry<String, BigDecimal> entry : st.getCoMarks().entrySet()) {
                    String coCode = entry.getKey();
                    BigDecimal marksObtained = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
                    BigDecimal maxMarks = coMaxMarks.getOrDefault(coCode, new BigDecimal("100.00"));

                    StudentCoMark markEntity = StudentCoMark.builder()
                            .id("mrk-" + UUID.randomUUID().toString().substring(0, 8))
                            .programmeBatchCourseId(offeringId)
                            .studentId(studentId)
                            .prn(cleanPrn)
                            .studentName(studentName)
                            .coCode(coCode)
                            .marksObtained(marksObtained)
                            .maxMarks(maxMarks)
                            .build();
                    markEntities.add(markEntity);
                }
            }
        }

        if (!markEntities.isEmpty()) {
            studentCoMarkRepository.saveAll(markEntities);
        }
    }

    // =========================================================================
    //  LEVEL BAND EVALUATOR & PARSER HELPERS
    // =========================================================================

    public record LevelBand(int level, BigDecimal minPercentage, BigDecimal maxPercentage) {}

    public List<LevelBand> parseLevelBands(String levelsJson, boolean isDirect) {
        if (levelsJson != null && !levelsJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(levelsJson);
                if (root.isArray() && root.size() > 0) {
                    List<LevelBand> bands = new ArrayList<>();
                    for (JsonNode node : root) {
                        int level = node.has("level") ? node.get("level").asInt() : bands.size() + 1;
                        BigDecimal min = node.has("minPercentage") ? new BigDecimal(node.get("minPercentage").asText()) :
                                (node.has("min") ? new BigDecimal(node.get("min").asText()) : BigDecimal.ZERO);
                        BigDecimal max = node.has("maxPercentage") ? new BigDecimal(node.get("maxPercentage").asText()) :
                                (node.has("max") ? new BigDecimal(node.get("max").asText()) : new BigDecimal("100.00"));

                        if (min.compareTo(max) > 0) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Invalid level band configuration: minPercentage (" + min + ") cannot be greater than maxPercentage (" + max + ").");
                        }
                        if (min.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(new BigDecimal("100.00")) > 0) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Invalid level band configuration: percentages must be within 0 to 100. Found [" + min + ", " + max + "].");
                        }
                        bands.add(new LevelBand(level, min, max));
                    }
                    bands.sort(Comparator.comparing(LevelBand::minPercentage));
                    return bands;
                }
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception e) {
                log.warn("Failed to parse custom {} levels JSON: {}. Using default level bands.", isDirect ? "direct" : "indirect", levelsJson);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid level band JSON format: " + e.getMessage());
            }
        }

        // Default standard 3-tier bands
        return List.of(
                new LevelBand(1, new BigDecimal("0.00"), new BigDecimal("40.00")),
                new LevelBand(2, new BigDecimal("40.00"), new BigDecimal("60.00")),
                new LevelBand(3, new BigDecimal("60.00"), new BigDecimal("100.00"))
        );
    }

    public int evaluateLevelBand(BigDecimal percentage, List<LevelBand> bands) {
        if (bands == null || bands.isEmpty()) {
            bands = List.of(
                    new LevelBand(1, new BigDecimal("0.00"), new BigDecimal("40.00")),
                    new LevelBand(2, new BigDecimal("40.00"), new BigDecimal("60.00")),
                    new LevelBand(3, new BigDecimal("60.00"), new BigDecimal("100.00"))
            );
        }

        if (percentage == null) return 0;

        for (int i = 0; i < bands.size(); i++) {
            LevelBand band = bands.get(i);
            boolean isTopBand = (i == bands.size() - 1) || band.maxPercentage().compareTo(new BigDecimal("100.00")) >= 0;

            if (isTopBand) {
                if (percentage.compareTo(band.minPercentage()) >= 0 && percentage.compareTo(band.maxPercentage()) <= 0) {
                    return band.level();
                }
            } else {
                if (percentage.compareTo(band.minPercentage()) >= 0 && percentage.compareTo(band.maxPercentage()) < 0) {
                    return band.level();
                }
            }
        }

        if (percentage.compareTo(new BigDecimal("100.00")) > 0) {
            return bands.get(bands.size() - 1).level();
        } else if (percentage.compareTo(BigDecimal.ZERO) <= 0) {
            return bands.get(0).level();
        }

        return bands.get(0).level();
    }

    private Sheet findExaminationSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            String name = s.getSheetName();
            if (name != null && name.toLowerCase().matches(".*(examination|exam|direct).*")) {
                return s;
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            for (Row r : s) {
                for (Cell c : r) {
                    try {
                        String text = c.getStringCellValue();
                        if (text != null) {
                            String lower = text.toLowerCase();
                            if (lower.contains("direct method") || lower.contains("co assessment") || lower.contains("fraction of out of")) {
                                return s;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return workbook.getSheetAt(0);
    }

    private Sheet findSurveySheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            String name = s.getSheetName();
            if (name != null && name.toLowerCase().matches(".*(course\\s*end\\s*survey|survey|indirect).*")) {
                return s;
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            for (Row r : s) {
                for (Cell c : r) {
                    try {
                        String text = c.getStringCellValue();
                        if (text != null) {
                            String lower = text.toLowerCase();
                            if (lower.contains("indirect method") || lower.contains("substantial") || lower.contains("slight")) {
                                return s;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return workbook.getSheetAt(workbook.getNumberOfSheets() > 1 ? 1 : 0);
    }

    private BigDecimal extractThresholdFromSheet(Sheet sheet, FormulaEvaluator evaluator) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String str = null;
                try {
                    str = cell.getStringCellValue();
                } catch (Exception ignored) {}
                if (str != null) {
                    String lower = str.toLowerCase().trim();
                    if ((lower.contains("threshold") || lower.contains("threshhold"))
                            && !lower.contains("above") && !lower.contains("% of") && !lower.contains("fraction") && !lower.contains("reference")) {
                        int rIdx = row.getRowNum();
                        for (int rSearch = rIdx; rSearch <= Math.min(sheet.getLastRowNum(), rIdx + 1); rSearch++) {
                            Row r = sheet.getRow(rSearch);
                            if (r == null) continue;
                            for (Cell valCell : r) {
                                if (valCell == cell) continue;
                                Double numVal = getNumericCellValue(valCell, evaluator);
                                if (numVal != null && numVal > 0) {
                                    if (numVal > 0 && numVal <= 1.0) {
                                        numVal = numVal * 100.0;
                                    }
                                    BigDecimal thresh = BigDecimal.valueOf(numVal).setScale(2, RoundingMode.HALF_UP);
                                    if (thresh.compareTo(BigDecimal.ZERO) >= 0 && thresh.compareTo(new BigDecimal("100.00")) <= 0) {
                                        return thresh;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Double getNumericCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty() || "-".equals(str) || "--".equals(str)) return null;
                try {
                    return Double.parseDouble(str);
                } catch (NumberFormatException e) {
                    return null;
                }
            case FORMULA:
                if (evaluator != null) {
                    try {
                        CellValue cellValue = evaluator.evaluate(cell);
                        if (cellValue != null) {
                            if (cellValue.getCellType() == CellType.NUMERIC) {
                                return cellValue.getNumberValue();
                            } else if (cellValue.getCellType() == CellType.STRING) {
                                String s = cellValue.getStringValue().trim();
                                if (!s.isEmpty() && !"-".equals(s)) {
                                    try { return Double.parseDouble(s); } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                try {
                    return cell.getNumericCellValue();
                } catch (Exception ignored) {}
                return null;
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1.0 : 0.0;
            default:
                return null;
        }
    }

    private String getStringCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                long longVal = (long) cell.getNumericCellValue();
                if (cell.getNumericCellValue() == (double) longVal) {
                    return String.valueOf(longVal);
                }
                return String.valueOf(cell.getNumericCellValue());
            case FORMULA:
                if (evaluator != null) {
                    try {
                        CellValue cellValue = evaluator.evaluate(cell);
                        if (cellValue != null) {
                            if (cellValue.getCellType() == CellType.STRING) {
                                return cellValue.getStringValue().trim();
                            } else if (cellValue.getCellType() == CellType.NUMERIC) {
                                long lv = (long) cellValue.getNumberValue();
                                if (cellValue.getNumberValue() == (double) lv) {
                                    return String.valueOf(lv);
                                }
                                return String.valueOf(cellValue.getNumberValue());
                            }
                        }
                    } catch (Exception ignored) {}
                }
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception ignored) {}
                return "";
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    // =========================================================================
    //  EXAMINATION DIRECT ATTAINMENT ENGINE
    // =========================================================================

    public ExaminationAttainmentResultDto calculateExaminationAttainment(String courseOfferingOrMasterCourseId, ExaminationMarksPayloadDto payload) {
        log.debug("[AttainmentCalculationService] calculateExaminationAttainment called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        AttainmentConfiguration config = getApprovedAttainmentConfig(offeringId);

        if (payload == null || payload.getStudentMarks() == null || payload.getStudentMarks().isEmpty()) {
            return getExaminationAttainment(offeringId);
        }

        BigDecimal thresholdPct = payload.getThresholdPercentage() != null ? payload.getThresholdPercentage() :
                (config.getDirectThreshold() != null ? config.getDirectThreshold() : new BigDecimal("60.00"));
        Map<String, BigDecimal> coMaxMarks = payload.getCoMaxMarks() != null ? payload.getCoMaxMarks() : Collections.emptyMap();
        List<StudentMarksRowDto> studentMarks = payload.getStudentMarks();

        Map<String, BigDecimal> thresholdMarks = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : coMaxMarks.entrySet()) {
            BigDecimal max = e.getValue() != null ? e.getValue() : new BigDecimal("100.00");
            BigDecimal thresh = max.multiply(thresholdPct).divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
            thresholdMarks.put(e.getKey(), thresh);
        }

        Map<String, Integer> countAbove = new LinkedHashMap<>();
        Map<String, Integer> countTotal = new LinkedHashMap<>();

        for (String coCode : coMaxMarks.keySet()) {
            countAbove.put(coCode, 0);
            countTotal.put(coCode, 0);
        }

        for (StudentMarksRowDto student : studentMarks) {
            if (student.getCoMarks() == null) continue;
            for (Map.Entry<String, BigDecimal> e : student.getCoMarks().entrySet()) {
                String coCode = e.getKey();
                BigDecimal marks = e.getValue() != null ? e.getValue() : BigDecimal.ZERO;
                BigDecimal thresh = thresholdMarks.getOrDefault(coCode, BigDecimal.ZERO);

                countTotal.put(coCode, countTotal.getOrDefault(coCode, 0) + 1);
                if (marks.compareTo(thresh) >= 0) {
                    countAbove.put(coCode, countAbove.getOrDefault(coCode, 0) + 1);
                }
            }
        }

        Map<String, BigDecimal> percentageAbove = new LinkedHashMap<>();
        Map<String, Integer> attainmentLevels = new LinkedHashMap<>();

        List<LevelBand> directBands = parseLevelBands(config.getDirectLevelsJson(), true);

        double sumLevels = 0;
        int coCount = 0;

        for (String coCode : coMaxMarks.keySet()) {
            int total = countTotal.getOrDefault(coCode, 0);
            int above = countAbove.getOrDefault(coCode, 0);

            BigDecimal pct = total > 0
                    ? BigDecimal.valueOf(above).multiply(new BigDecimal("100.00")).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            percentageAbove.put(coCode, pct);

            int level = evaluateLevelBand(pct, directBands);
            attainmentLevels.put(coCode, level);
            sumLevels += level;
            coCount++;
        }

        BigDecimal overallDirectCoAttainment = coCount > 0
                ? BigDecimal.valueOf(sumLevels / coCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        ExaminationAttainmentResultDto result = ExaminationAttainmentResultDto.builder()
                .masterCourseId(offeringId)
                .thresholdPercentage(thresholdPct)
                .totalStudents(studentMarks.size())
                .coMaxMarks(coMaxMarks)
                .coThresholdMarks(thresholdMarks)
                .studentMarks(studentMarks)
                .studentsAboveThreshold(countAbove)
                .percentageAboveThreshold(percentageAbove)
                .coAttainmentLevels(attainmentLevels)
                .overallDirectCoAttainment(overallDirectCoAttainment)
                .build();

        examinationAttainmentStore.put(offeringId, result);
        return result;
    }

    @Transactional
    public void deleteExaminationData(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] deleteExaminationData called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);

        // 1. Delete physical files
        List<UploadedDocument> docs = uploadedDocumentRepository.findByProgrammeBatchCourseId(offeringId);
        for (UploadedDocument doc : docs) {
            if (doc.getDocumentType() == DocumentType.EXAMINATION) {
                if (doc.getSavedPath() != null) {
                    try {
                        Files.deleteIfExists(Path.of(doc.getSavedPath()));
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Delete database records
        uploadedDocumentRepository.deleteByProgrammeBatchCourseIdAndDocumentType(offeringId, DocumentType.EXAMINATION);
        studentCoMarkRepository.deleteByProgrammeBatchCourseId(offeringId);
        studentCoMarkRepository.flush();

        // 3. Clear in-memory store
        examinationAttainmentStore.remove(offeringId);

        // 4. Reset approval to DRAFT if applicable
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.ATTAINMENT_SETTINGS, offeringId, null);
        }
    }

    @Transactional
    public void deleteSurveyData(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] deleteSurveyData called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);

        // 1. Delete physical files
        List<UploadedDocument> docs = uploadedDocumentRepository.findByProgrammeBatchCourseId(offeringId);
        for (UploadedDocument doc : docs) {
            if (doc.getDocumentType() == DocumentType.SURVEY) {
                if (doc.getSavedPath() != null) {
                    try {
                        Files.deleteIfExists(Path.of(doc.getSavedPath()));
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Delete database records
        uploadedDocumentRepository.deleteByProgrammeBatchCourseIdAndDocumentType(offeringId, DocumentType.SURVEY);

        // 3. Clear in-memory store
        surveyAttainmentStore.remove(offeringId);

        // 4. Reset approval to DRAFT if applicable
        if (approvalService != null) {
            approvalService.resetToDraftOnModification(ApprovalType.ATTAINMENT_SETTINGS, offeringId, null);
        }
    }

    @Transactional
    public ExaminationAttainmentResultDto processAndSaveExaminationFile(String courseOfferingOrMasterCourseId, MultipartFile file, BigDecimal thresholdPercentage, String uploadedBy) {
        log.debug("[AttainmentCalculationService] processAndSaveExaminationFile called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + offeringId));
        String programmeBatchId = offering.getProgrammeBatchId();

        AttainmentConfiguration config = getAttainmentConfig(offeringId);

        BigDecimal threshold = thresholdPercentage;
        Map<String, BigDecimal> coMaxMarks = new LinkedHashMap<>();
        List<StudentMarksRowDto> studentList = new ArrayList<>();

        if (file != null && !file.isEmpty()) {
            Path targetFilePath = null;
            try {
                targetFilePath = saveUploadedFile(file, "examination", offeringId);
                String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "exam_marks.xlsx";
                String savedFileName = targetFilePath.getFileName().toString();
                String savedPath = targetFilePath.toAbsolutePath().toString();
                try (InputStream is = Files.newInputStream(targetFilePath);
                     Workbook workbook = WorkbookFactory.create(is)) {
                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    Sheet sheet = findExaminationSheet(workbook);

                    // 1. Threshold extraction
                    BigDecimal extractedThreshold = extractThresholdFromSheet(sheet, evaluator);
                    if (extractedThreshold != null) {
                        threshold = extractedThreshold;
                    } else if (threshold == null) {
                        threshold = config.getDirectThreshold() != null ? config.getDirectThreshold() : new BigDecimal("60.00");
                    }

                    // 2. Locate CO header row & map CO columns
                    List<CourseOutcome> configuredCos = getConfiguredCourseOutcomes(offeringId, offering.getMasterCourseId());
                    Set<String> configuredCoCodes = configuredCos.stream()
                            .map(c -> normalizeOutcomeCode(c.getCode()))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    int coHeaderRowNum = -1;
                    Map<Integer, String> coColMap = new LinkedHashMap<>();
                    Set<String> duplicateCodes = new LinkedHashSet<>();

                    for (Row row : sheet) {
                        Map<Integer, String> candidateCols = new LinkedHashMap<>();
                        Set<String> candidateDups = new LinkedHashSet<>();
                        Set<String> seen = new HashSet<>();
                        for (Cell cell : row) {
                            String text = getStringCellValue(cell, evaluator);
                            if (text != null && text.matches("(?i)^CO\\s*\\d+$")) {
                                String clean = normalizeOutcomeCode(text);
                                if (clean != null) {
                                    if (!seen.add(clean)) candidateDups.add(clean);
                                    candidateCols.put(cell.getColumnIndex(), clean);
                                }
                            }
                        }
                        if (candidateCols.size() > coColMap.size()) {
                            coColMap = candidateCols;
                            duplicateCodes = candidateDups;
                            coHeaderRowNum = row.getRowNum();
                        }
                    }

                    if (coColMap.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Course Outcome (CO) headers found in Direct Examination sheet.");
                    }

                    if (!configuredCoCodes.isEmpty()) {
                        if (!duplicateCodes.isEmpty()) {
                            String dup = duplicateCodes.iterator().next();
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct Examination sheet contains duplicate Course Outcome " + dup + ".");
                        }

                        for (String foundCo : coColMap.values()) {
                            if (!configuredCoCodes.contains(foundCo)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct Examination sheet contains unconfigured Course Outcome " + foundCo + ". Configured COs for this course: " + configuredCoCodes + ".");
                            }
                        }

                        for (String cfgCo : configuredCoCodes) {
                            if (!coColMap.containsValue(cfgCo)) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direct Examination sheet is missing configured Course Outcome " + cfgCo + ". Configured COs for this course: " + configuredCoCodes + ".");
                            }
                        }

                        if (coColMap.size() != configuredCoCodes.size()) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Outcome count mismatch. Direct Examination sheet contains " + coColMap.size() + " COs, but course requires " + configuredCoCodes.size() + " COs (" + configuredCoCodes + ").");
                        }
                    }

                    // 3. Locate Out Of row
                    int outOfRowNum = -1;
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            String text = getStringCellValue(cell, evaluator);
                            if (text != null && text.matches("(?i)^out\\s*of.*|^max(\\.?\\s*marks)?.*|^maximum.*")) {
                                outOfRowNum = row.getRowNum();
                                break;
                            }
                        }
                        if (outOfRowNum != -1) break;
                    }

                    if (outOfRowNum != -1) {
                        Row outOfRow = sheet.getRow(outOfRowNum);
                        for (Map.Entry<Integer, String> e : coColMap.entrySet()) {
                            int colIdx = e.getKey();
                            String coCode = e.getValue();
                            Cell c = outOfRow.getCell(colIdx);
                            Double val = getNumericCellValue(c, evaluator);
                            if (val == null || val <= 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Invalid or missing 'Out Of' marks for " + coCode + ". Out Of marks must be greater than zero.");
                            }
                            coMaxMarks.put(coCode, BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP));
                        }
                    } else {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find 'Out Of' or 'Max Marks' row indicating maximum marks for COs.");
                    }

                    // 4. Locate PRN and Name columns
                    int prnCol = -1;
                    int nameCol = -1;
                    for (int r = Math.max(0, coHeaderRowNum - 3); r <= coHeaderRowNum; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;
                        for (Cell cell : row) {
                            String text = getStringCellValue(cell, evaluator);
                            if (text != null) {
                                String lower = text.toLowerCase();
                                if (lower.contains("prn") || lower.contains("roll") || lower.contains("student id") || lower.contains("reg")) {
                                    prnCol = cell.getColumnIndex();
                                } else if (lower.contains("name") || lower.contains("student name")) {
                                    nameCol = cell.getColumnIndex();
                                }
                            }
                        }
                    }
                    if (prnCol == -1) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find a column header for 'PRN' or 'Roll Number'.");
                    }
                    if (nameCol == -1) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find a column header for 'Name'.");
                    }

                    // 5. Parse student mark records
                    for (int r = coHeaderRowNum + 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        Cell prnCell = row.getCell(prnCol);
                        String prn = getStringCellValue(prnCell, evaluator);

                        Cell nameCell = nameCol >= 0 ? row.getCell(nameCol) : null;
                        String name = getStringCellValue(nameCell, evaluator);

                        if (prn == null || prn.isBlank()) {
                            continue;
                        }

                        if (prn == null || prn.isBlank()) continue;

                        String prnLower = prn.toLowerCase();
                        if (r == outOfRowNum || prnLower.contains("average") || prnLower.contains("total") || prnLower.contains("threshold") || prnLower.contains("count") || prnLower.contains("target") || prnLower.contains("max") || prnLower.contains("out of")) {
                            continue;
                        }

                        Map<String, BigDecimal> coMarks = new LinkedHashMap<>();
                        for (Map.Entry<Integer, String> e : coColMap.entrySet()) {
                            int colIdx = e.getKey();
                            String coCode = e.getValue();
                            BigDecimal outOf = coMaxMarks.getOrDefault(coCode, new BigDecimal("100.00"));
                            Cell markCell = row.getCell(colIdx);

                            Double numMark = getNumericCellValue(markCell, evaluator);
                            if (numMark == null) {
                                String strMark = getStringCellValue(markCell, evaluator);
                                if (strMark.isEmpty() || "-".equals(strMark) || "--".equals(strMark) || "ab".equalsIgnoreCase(strMark) || "a".equalsIgnoreCase(strMark)) {
                                    numMark = 0.0;
                                } else {
                                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                            "Invalid non-numeric mark '" + strMark + "' for student PRN '" + prn + "' in " + coCode);
                                }
                            }

                            BigDecimal mark = BigDecimal.valueOf(numMark).setScale(2, RoundingMode.HALF_UP);
                            if (mark.compareTo(BigDecimal.ZERO) < 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Invalid mark " + mark + " for student PRN '" + prn + "' in " + coCode + ". Mark must be non-negative.");
                            }
                            if (mark.compareTo(outOf) > 0) {
                                log.warn("Student PRN '{}' mark {} for {} exceeds Out Of {}. Clamping mark to max.", prn, mark, coCode, outOf);
                                mark = outOf;
                            }
                            coMarks.put(coCode, mark);
                        }

                        studentList.add(StudentMarksRowDto.builder()
                                .srNo(studentList.size() + 1)
                                .prn(prn)
                                .studentName(name != null && !name.isBlank() ? name : "Student " + (studentList.size() + 1))
                                .coMarks(coMarks)
                                .build());
                    }
                }

                if (studentList.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid student mark records found in the Examination sheet.");
                }

                // Update Course Offering's AttainmentConfiguration with authoritative uploaded threshold
                config.setDirectThreshold(threshold);
                config.setUpdatedAt(ZonedDateTime.now());
                configRepository.save(config);

                // Persist Student marks and audit document
                saveStudentCoMarksToDatabase(offeringId, coMaxMarks, studentList);

                uploadedDocumentRepository.deleteByProgrammeBatchCourseIdAndDocumentType(offeringId, DocumentType.EXAMINATION);
                UploadedDocument doc = UploadedDocument.builder()
                        .id("doc-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchCourseId(offeringId)
                        .programmeBatchId(programmeBatchId)
                        .documentType(DocumentType.EXAMINATION)
                        .fileName(originalFilename)
                        .savedFileName(savedFileName)
                        .savedPath(savedPath)
                        .fileSize(file.getSize())
                        .recordsProcessed(studentList.size())
                        .thresholdPercentage(threshold)
                        .uploadedBy(uploadedBy != null ? uploadedBy : "Course Coordinator")
                        .uploadedAt(ZonedDateTime.now())
                        .build();
                uploadedDocumentRepository.save(doc);

            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception e) {
                log.error("  [EXAM FILE PROCESS ERROR]: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to process examination file: " + e.getMessage());
            }
        }

        ExaminationMarksPayloadDto payload = ExaminationMarksPayloadDto.builder()
                .masterCourseId(offeringId)
                .thresholdPercentage(threshold)
                .coMaxMarks(coMaxMarks)
                .studentMarks(studentList)
                .build();

        return calculateExaminationAttainment(offeringId, payload);
    }

    @Transactional(readOnly = true)
    public ExaminationAttainmentResultDto getExaminationAttainment(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] getExaminationAttainment called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        if (examinationAttainmentStore.containsKey(offeringId)) {
            return examinationAttainmentStore.get(offeringId);
        }

        List<StudentCoMark> dbMarks = studentCoMarkRepository.findByProgrammeBatchCourseId(offeringId);
        if (!dbMarks.isEmpty()) {
            Map<String, BigDecimal> coMaxMarks = new LinkedHashMap<>();
            Map<String, Map<String, BigDecimal>> studentCoMarksMap = new LinkedHashMap<>();
            Map<String, String> studentNamesMap = new LinkedHashMap<>();

            for (StudentCoMark mark : dbMarks) {
                String prn = mark.getPrn();
                String coCode = mark.getCoCode();

                if (!coMaxMarks.containsKey(coCode) && mark.getMaxMarks() != null) {
                    coMaxMarks.put(coCode, mark.getMaxMarks());
                }

                studentCoMarksMap.putIfAbsent(prn, new LinkedHashMap<>());
                studentCoMarksMap.get(prn).put(coCode, mark.getMarksObtained());
                studentNamesMap.put(prn, mark.getStudentName());
            }

            List<StudentMarksRowDto> studentList = new ArrayList<>();
            int srNo = 1;
            for (Map.Entry<String, Map<String, BigDecimal>> entry : studentCoMarksMap.entrySet()) {
                String prn = entry.getKey();
                studentList.add(StudentMarksRowDto.builder()
                        .srNo(srNo++)
                        .prn(prn)
                        .studentName(studentNamesMap.getOrDefault(prn, "Student " + srNo))
                        .coMarks(entry.getValue())
                        .build());
            }

            BigDecimal threshold = getApprovedAttainmentConfig(offeringId).getDirectThreshold();
            if (threshold == null) threshold = new BigDecimal("60.00");
            Optional<UploadedDocument> docOpt = uploadedDocumentRepository
                    .findFirstByProgrammeBatchCourseIdAndDocumentTypeOrderByUploadedAtDesc(offeringId, DocumentType.EXAMINATION);
            if (docOpt.isPresent() && docOpt.get().getThresholdPercentage() != null) {
                threshold = docOpt.get().getThresholdPercentage();
            }

            ExaminationMarksPayloadDto payload = ExaminationMarksPayloadDto.builder()
                    .masterCourseId(offeringId)
                    .thresholdPercentage(threshold)
                    .coMaxMarks(coMaxMarks)
                    .studentMarks(studentList)
                    .build();

            return calculateExaminationAttainment(offeringId, payload);
        }

        return ExaminationAttainmentResultDto.builder()
                .masterCourseId(offeringId)
                .thresholdPercentage(getApprovedAttainmentConfig(offeringId).getDirectThreshold() != null ? getApprovedAttainmentConfig(offeringId).getDirectThreshold() : new BigDecimal("60.00"))
                .totalStudents(0)
                .coMaxMarks(Collections.emptyMap())
                .coThresholdMarks(Collections.emptyMap())
                .studentMarks(Collections.emptyList())
                .studentsAboveThreshold(Collections.emptyMap())
                .percentageAboveThreshold(Collections.emptyMap())
                .coAttainmentLevels(Collections.emptyMap())
                .overallDirectCoAttainment(BigDecimal.ZERO)
                .build();
    }

    // =========================================================================
    //  COURSE END SURVEY INDIRECT ATTAINMENT ENGINE
    // =========================================================================

    public SurveyAttainmentResultDto calculateSurveyAttainment(String courseOfferingOrMasterCourseId, SurveyMarksPayloadDto payload) {
        log.debug("[AttainmentCalculationService] calculateSurveyAttainment called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        AttainmentConfiguration config = getApprovedAttainmentConfig(offeringId);

        if (payload == null || payload.getSurveyResponses() == null || payload.getSurveyResponses().isEmpty()) {
            return getSurveyAttainment(offeringId);
        }

        List<SurveyResponseRowDto> responses = payload.getSurveyResponses();
        Set<String> coCodes = new LinkedHashSet<>();
        for (SurveyResponseRowDto r : responses) {
            if (r.getCoRatings() != null) coCodes.addAll(r.getCoRatings().keySet());
        }
        if (coCodes.isEmpty()) {
            coCodes = Set.of("CO1", "CO2", "CO3", "CO4", "CO5");
        }

        int totalStudents = responses.size();
        Map<String, Integer> level1Counts = new LinkedHashMap<>();
        Map<String, Integer> level2Counts = new LinkedHashMap<>();
        Map<String, Integer> level3Counts = new LinkedHashMap<>();
        Map<String, BigDecimal> level1Percentages = new LinkedHashMap<>();
        Map<String, BigDecimal> level2Percentages = new LinkedHashMap<>();
        Map<String, BigDecimal> level3Percentages = new LinkedHashMap<>();
        Map<String, BigDecimal> overallIndirectPercentages = new LinkedHashMap<>();
        Map<String, BigDecimal> indirectAttainmentScores = new LinkedHashMap<>();
        Map<String, Integer> coAttainmentLevels = new LinkedHashMap<>();

        List<LevelBand> indirectBands = parseLevelBands(config.getIndirectLevelsJson(), false);

        double sumScores = 0;
        int coCount = 0;

        for (String co : coCodes) {
            int count1 = 0;
            int count2 = 0;
            int count3 = 0;

            for (SurveyResponseRowDto r : responses) {
                if (r.getCoRatings() != null && r.getCoRatings().containsKey(co)) {
                    BigDecimal rating = r.getCoRatings().get(co);
                    if (rating != null) {
                        int rInt = (int) Math.round(rating.doubleValue());
                        if (rInt == 1) count1++;
                        else if (rInt == 2) count2++;
                        else if (rInt == 3) count3++;
                    }
                }
            }

            int validResponses = count1 + count2 + count3;
            int divisor = validResponses > 0 ? validResponses : (totalStudents > 0 ? totalStudents : 1);

            double pct1Raw = (double) count1 * 100.0 / divisor;
            double pct2Raw = (double) count2 * 100.0 / divisor;
            double pct3Raw = (double) count3 * 100.0 / divisor;

            BigDecimal pct1 = BigDecimal.valueOf(pct1Raw).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pct2 = BigDecimal.valueOf(pct2Raw).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pct3 = BigDecimal.valueOf(pct3Raw).setScale(2, RoundingMode.HALF_UP);

            // Official workbook formula: overallIndirectPercentage = (pct1 * 0.33 + pct2 * 0.67 + pct3 * 1.0)
            double overallIndirectPct = (pct1Raw * 0.33) + (pct2Raw * 0.67) + (pct3Raw * 1.0);
            BigDecimal overallPct = BigDecimal.valueOf(overallIndirectPct).setScale(2, RoundingMode.HALF_UP);

            // Indirect score out of 3.00
            double scoreVal = (count1 * 1.0 + count2 * 2.0 + count3 * 3.0) / divisor;
            // Dynamic Level Band evaluation (compare overall percentage directly to configured bands -> 1, 2, or 3):
            int level = evaluateLevelBand(overallPct, indirectBands);

            level1Counts.put(co, count1);
            level2Counts.put(co, count2);
            level3Counts.put(co, count3);
            level1Percentages.put(co, pct1);
            level2Percentages.put(co, pct2);
            level3Percentages.put(co, pct3);
            overallIndirectPercentages.put(co, overallPct);
            indirectAttainmentScores.put(co, BigDecimal.valueOf(level));
            coAttainmentLevels.put(co, level);

            sumScores += level;
            coCount++;
        }

        BigDecimal overallIndirectCoAttainment = coCount > 0
                ? BigDecimal.valueOf(sumScores / coCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        SurveyAttainmentResultDto result = SurveyAttainmentResultDto.builder()
                .masterCourseId(offeringId)
                .totalStudents(totalStudents)
                .level1Counts(level1Counts)
                .level2Counts(level2Counts)
                .level3Counts(level3Counts)
                .level1Percentages(level1Percentages)
                .level2Percentages(level2Percentages)
                .level3Percentages(level3Percentages)
                .overallIndirectPercentages(overallIndirectPercentages)
                .indirectAttainmentScores(indirectAttainmentScores)
                .coAttainmentLevels(coAttainmentLevels)
                .overallIndirectCoAttainment(overallIndirectCoAttainment)
                .surveyResponses(responses)
                .build();

        surveyAttainmentStore.put(offeringId, result);
        return result;
    }

    @Transactional
    public SurveyAttainmentResultDto processAndSaveSurveyFile(String courseOfferingOrMasterCourseId, MultipartFile file, BigDecimal thresholdPercentage, String uploadedBy) {
        log.debug("[AttainmentCalculationService] processAndSaveSurveyFile called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        enforceOfferingEditability(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + offeringId));
        String programmeBatchId = offering.getProgrammeBatchId();

        List<SurveyResponseRowDto> surveyResponses = new ArrayList<>();

        if (file != null && !file.isEmpty()) {
            Path targetFilePath = null;
            try {
                targetFilePath = saveUploadedFile(file, "survey", offeringId);
                String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "survey_responses.xlsx";
                String savedFileName = targetFilePath.getFileName().toString();
                String savedPath = targetFilePath.toAbsolutePath().toString();
                List<CourseOutcome> configuredCos = getConfiguredCourseOutcomes(offeringId, offering.getMasterCourseId());
                Set<String> configuredCoCodes = configuredCos.stream()
                        .map(c -> normalizeOutcomeCode(c.getCode()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                try (InputStream is = Files.newInputStream(targetFilePath);
                     Workbook workbook = WorkbookFactory.create(is)) {
                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    Sheet sheet = findSurveySheet(workbook);
                    surveyResponses = parseSurveySheet(sheet, evaluator, configuredCoCodes);
                }

                if (surveyResponses.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid survey response rows found in the Survey sheet.");
                }

                // Auto-register student PRNs if not already present
                Set<String> surveyPrnSet = surveyResponses.stream()
                        .map(SurveyResponseRowDto::getPrn)
                        .filter(prn -> prn != null && !prn.isBlank() && !prn.startsWith("SRV-"))
                        .map(String::trim)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                Map<String, Student> existingSurveyStudents = surveyPrnSet.isEmpty()
                        ? new HashMap<>()
                        : studentRepository.findByPrnIn(surveyPrnSet).stream()
                                .filter(s -> s.getPrn() != null)
                                .collect(Collectors.toMap(s -> s.getPrn().trim(), Function.identity(), (a, b) -> a, LinkedHashMap::new));

                List<Student> surveyStudentsToSave = new ArrayList<>();
                Map<String, Student> effectiveSurveyStudents = new LinkedHashMap<>(existingSurveyStudents);

                for (SurveyResponseRowDto sr : surveyResponses) {
                    String prn = sr.getPrn();
                    if (prn != null && !prn.isBlank() && !prn.startsWith("SRV-")) {
                        String cleanPrn = prn.trim();
                        Student student = effectiveSurveyStudents.get(cleanPrn);
                        if (student == null) {
                            Student newStudent = Student.builder()
                                    .id("std-" + UUID.randomUUID().toString().substring(0, 8))
                                    .prn(cleanPrn)
                                    .name(sr.getStudentName() != null && !sr.getStudentName().isBlank() ? sr.getStudentName().trim() : "Student " + cleanPrn)
                                    .email(cleanPrn.toLowerCase() + "@dypiu.ac.in")
                                    .programmeBatchId(programmeBatchId)
                                    .status(StudentStatus.ENROLLED)
                                    .build();
                            effectiveSurveyStudents.put(cleanPrn, newStudent);
                            surveyStudentsToSave.add(newStudent);
                        } else if (student.getProgrammeBatchId() == null) {
                            student.setProgrammeBatchId(programmeBatchId);
                            surveyStudentsToSave.add(student);
                        }
                    }
                }

                if (!surveyStudentsToSave.isEmpty()) {
                    studentRepository.saveAll(surveyStudentsToSave);
                }

                uploadedDocumentRepository.deleteByProgrammeBatchCourseIdAndDocumentType(offeringId, DocumentType.SURVEY);
                UploadedDocument doc = UploadedDocument.builder()
                        .id("doc-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchCourseId(offeringId)
                        .programmeBatchId(programmeBatchId)
                        .documentType(DocumentType.SURVEY)
                        .fileName(originalFilename)
                        .savedFileName(savedFileName)
                        .savedPath(savedPath)
                        .fileSize(file.getSize())
                        .recordsProcessed(surveyResponses.size())
                        .thresholdPercentage(thresholdPercentage)
                        .uploadedBy(uploadedBy != null ? uploadedBy : "Course Coordinator")
                        .uploadedAt(ZonedDateTime.now())
                        .build();
                uploadedDocumentRepository.save(doc);

            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception e) {
                log.error("  [SURVEY FILE PROCESS ERROR]: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to process survey file: " + e.getMessage());
            }
        }

        SurveyMarksPayloadDto payload = SurveyMarksPayloadDto.builder()
                .masterCourseId(offeringId)
                .surveyResponses(surveyResponses)
                .build();

        return calculateSurveyAttainment(offeringId, payload);
    }

    public List<SurveyResponseRowDto> parseSurveyFile(Path filePath) throws Exception {
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = findSurveySheet(workbook);
            return parseSurveySheet(sheet, evaluator, Collections.emptySet());
        }
    }

    public List<SurveyResponseRowDto> parseSurveySheet(Sheet sheet, FormulaEvaluator evaluator) {
        return parseSurveySheet(sheet, evaluator, Collections.emptySet());
    }

    public List<SurveyResponseRowDto> parseSurveySheet(Sheet sheet, FormulaEvaluator evaluator, Set<String> configuredCoCodes) {
        List<SurveyResponseRowDto> responses = new ArrayList<>();
        int coHeaderRowNum = -1;
        Map<Integer, String> coColMap = new LinkedHashMap<>();
        Set<String> duplicateCodes = new LinkedHashSet<>();

        for (Row row : sheet) {
            Map<Integer, String> candidateCols = new LinkedHashMap<>();
            Set<String> candidateDups = new LinkedHashSet<>();
            Set<String> seen = new HashSet<>();
            for (Cell cell : row) {
                String text = getStringCellValue(cell, evaluator);
                if (text != null && text.matches("(?i)^CO\\s*\\d+$")) {
                    String clean = normalizeOutcomeCode(text);
                    if (clean != null) {
                        if (!seen.add(clean)) candidateDups.add(clean);
                        candidateCols.put(cell.getColumnIndex(), clean);
                    }
                }
            }
            if (candidateCols.size() >= coColMap.size() && !candidateCols.isEmpty()) {
                coColMap = candidateCols;
                duplicateCodes = candidateDups;
                coHeaderRowNum = row.getRowNum();
            }
        }

        if (coColMap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Course Outcome (CO) headers found in Course End Survey sheet.");
        }

        if (configuredCoCodes != null && !configuredCoCodes.isEmpty()) {
            if (!duplicateCodes.isEmpty()) {
                String dup = duplicateCodes.iterator().next();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course End Survey sheet contains duplicate Course Outcome " + dup + ".");
            }

            for (String foundCo : coColMap.values()) {
                if (!configuredCoCodes.contains(foundCo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course End Survey sheet contains unconfigured Course Outcome " + foundCo + ". Configured COs for this course: " + configuredCoCodes + ".");
                }
            }

            for (String cfgCo : configuredCoCodes) {
                if (!coColMap.containsValue(cfgCo)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course End Survey sheet is missing configured Course Outcome " + cfgCo + ". Configured COs for this course: " + configuredCoCodes + ".");
                }
            }

            if (coColMap.size() != configuredCoCodes.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course Outcome count mismatch. Course End Survey sheet contains " + coColMap.size() + " COs, but course requires " + configuredCoCodes.size() + " COs (" + configuredCoCodes + ").");
            }
        }

        int prnColIdx = -1;
        int nameColIdx = -1;
        if (coHeaderRowNum >= 0) {
            Row hRow = sheet.getRow(coHeaderRowNum);
            if (hRow != null) {
                for (Cell cell : hRow) {
                    String hText = getStringCellValue(cell, evaluator);
                    if (hText != null) {
                        String clean = hText.toLowerCase().replaceAll("[^a-z]", "");
                        if (clean.contains("prn") || clean.contains("rollno") || clean.contains("studentid")) {
                            prnColIdx = cell.getColumnIndex();
                        } else if (clean.contains("name") || clean.contains("studentname")) {
                            nameColIdx = cell.getColumnIndex();
                        }
                    }
                }
            }
        }

        for (int r = coHeaderRowNum + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Map<String, BigDecimal> coRatings = new LinkedHashMap<>();
            Map<String, String> coFeedbacks = new LinkedHashMap<>();
            boolean hasAnyRating = false;

            for (Map.Entry<Integer, String> e : coColMap.entrySet()) {
                int colIdx = e.getKey();
                String coCode = e.getValue();
                Cell cell = row.getCell(colIdx);

                Double numVal = getNumericCellValue(cell, evaluator);
                String strVal = getStringCellValue(cell, evaluator);

                if (numVal != null && numVal >= 1 && numVal <= 3) {
                    int valInt = (int) Math.round(numVal);
                    coRatings.put(coCode, BigDecimal.valueOf(valInt).setScale(2, RoundingMode.HALF_UP));
                    String label = valInt == 3 ? "Substantial" : (valInt == 2 ? "Moderate" : "Slight");
                    coFeedbacks.put(coCode, label);
                    hasAnyRating = true;
                } else if (strVal != null && !strVal.isBlank()) {
                    String trimmed = strVal.trim();
                    if ("substantial".equalsIgnoreCase(trimmed) || "3".equals(trimmed) || "3.0".equals(trimmed) || "high".equalsIgnoreCase(trimmed) || "3 - substantial".equalsIgnoreCase(trimmed)) {
                        coRatings.put(coCode, new BigDecimal("3.00"));
                        coFeedbacks.put(coCode, "Substantial");
                        hasAnyRating = true;
                    } else if ("moderate".equalsIgnoreCase(trimmed) || "2".equals(trimmed) || "2.0".equals(trimmed) || "medium".equalsIgnoreCase(trimmed) || "2 - moderate".equalsIgnoreCase(trimmed)) {
                        coRatings.put(coCode, new BigDecimal("2.00"));
                        coFeedbacks.put(coCode, "Moderate");
                        hasAnyRating = true;
                    } else if ("slight".equalsIgnoreCase(trimmed) || "1".equals(trimmed) || "1.0".equals(trimmed) || "low".equalsIgnoreCase(trimmed) || "1 - slight".equalsIgnoreCase(trimmed)) {
                        coRatings.put(coCode, new BigDecimal("1.00"));
                        coFeedbacks.put(coCode, "Slight");
                        hasAnyRating = true;
                    } else if ("-".equals(trimmed) || "--".equals(trimmed)) {
                        // Empty rating
                    } else {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Invalid survey response '" + strVal + "' for " + coCode + ". Valid values are 'Slight', 'Moderate', 'Substantial' or 1, 2, 3.");
                    }
                }
            }

            if (!hasAnyRating) continue;

            String prn = null;
            if (prnColIdx != -1) {
                Cell pCell = row.getCell(prnColIdx);
                String rawPrn = getStringCellValue(pCell, evaluator);
                if (rawPrn != null && !rawPrn.isBlank()) {
                    prn = rawPrn.trim();
                }
            }

            String sName = null;
            if (nameColIdx != -1) {
                Cell nCell = row.getCell(nameColIdx);
                String rawName = getStringCellValue(nCell, evaluator);
                if (rawName != null && !rawName.isBlank()) {
                    sName = rawName.trim();
                }
            }

            if (prn == null || prn.isBlank()) {
                prn = "SRV-" + (responses.size() + 1);
            }

            int srNo = responses.size() + 1;
            responses.add(SurveyResponseRowDto.builder()
                    .srNo(srNo)
                    .prn(prn)
                    .studentName(sName != null && !sName.isBlank() ? sName : prn)
                    .coRatings(coRatings)
                    .coFeedbacks(coFeedbacks)
                    .build());
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public SurveyAttainmentResultDto getSurveyAttainment(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] getSurveyAttainment called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        if (surveyAttainmentStore.containsKey(offeringId)) {
            return surveyAttainmentStore.get(offeringId);
        }

        Optional<UploadedDocument> docOpt = uploadedDocumentRepository
                .findFirstByProgrammeBatchCourseIdAndDocumentTypeOrderByUploadedAtDesc(offeringId, DocumentType.SURVEY);
        if (docOpt.isPresent()) {
            String savedPath = docOpt.get().getSavedPath();
            if (savedPath != null && Files.exists(Paths.get(savedPath))) {
                try {
                    List<SurveyResponseRowDto> surveyResponses = parseSurveyFile(Paths.get(savedPath));
                    if (!surveyResponses.isEmpty()) {
                        SurveyMarksPayloadDto payload = SurveyMarksPayloadDto.builder()
                                .masterCourseId(offeringId)
                                .surveyResponses(surveyResponses)
                                .build();
                        return calculateSurveyAttainment(offeringId, payload);
                    }
                } catch (Exception e) {
                    log.warn("Failed to reload survey from saved file: {}", e.getMessage());
                }
            }
        }

        return SurveyAttainmentResultDto.builder()
                .masterCourseId(offeringId)
                .surveyResponses(Collections.emptyList())
                .indirectAttainmentScores(Collections.emptyMap())
                .overallIndirectPercentages(Collections.emptyMap())
                .coAttainmentLevels(Collections.emptyMap())
                .overallIndirectCoAttainment(BigDecimal.ZERO)
                .build();
    }

    // =========================================================================
    //  COMBINED CO ATTAINMENT CALCULATION
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> calculateCourseCoAttainment(String courseOfferingOrMasterCourseId) {
        log.debug("[AttainmentCalculationService] calculateCourseCoAttainment called | courseOfferingOrMasterCourseId: " + courseOfferingOrMasterCourseId);
        enforceOfferingOrCourseScope(courseOfferingOrMasterCourseId);
        String offeringId = resolveOfferingId(courseOfferingOrMasterCourseId);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId).orElse(null);
        String masterCourseId = offering != null ? offering.getMasterCourseId() : offeringId;

        AttainmentConfiguration config = getApprovedAttainmentConfig(offeringId);
        List<CourseOutcome> cos = courseOutcomeRepository.findByProgrammeBatchCourseId(offeringId);

        ExaminationAttainmentResultDto examResult = getExaminationAttainment(offeringId);
        SurveyAttainmentResultDto surveyResult = getSurveyAttainment(offeringId);

        if (cos.isEmpty()) {
            cos = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                cos.add(CourseOutcome.builder()
                        .id("co-default-" + i)
                        .programmeBatchCourseId(offeringId)
                        .code("CO" + i)
                        .statement("Course outcome CO" + i)
                        .targetLevel(new BigDecimal("2.50"))
                        .build());
            }
        }

        List<Map<String, Object>> coResults = new ArrayList<>();
        BigDecimal sumCoAttainment = BigDecimal.ZERO;
        int countCOs = 0;

        for (int i = 0; i < cos.size(); i++) {
            CourseOutcome co = cos.get(i);
            String coCode = co.getCode();
            String statement = co.getStatement() != null ? co.getStatement() : "Course outcome " + coCode;

            BigDecimal directPct = getCoMapValue(examResult.getPercentageAboveThreshold(), coCode, i, BigDecimal.ZERO);
            Integer directLevelObj = getCoMapValue(examResult.getCoAttainmentLevels(), coCode, i, 0);
            int directLevel = directLevelObj != null ? directLevelObj : evaluateLevelBand(directPct, parseLevelBands(config.getDirectLevelsJson(), true));

            BigDecimal indirectPct = getCoMapValue(surveyResult.getOverallIndirectPercentages(), coCode, i, BigDecimal.ZERO);
            BigDecimal indirectScore = getCoMapValue(surveyResult.getIndirectAttainmentScores(), coCode, i, BigDecimal.ZERO);
            Integer indirectLevelObj = getCoMapValue(surveyResult.getCoAttainmentLevels(), coCode, i, 0);
            int indirectLevel = indirectLevelObj != null ? indirectLevelObj : evaluateLevelBand(indirectPct, parseLevelBands(config.getIndirectLevelsJson(), false));

            double directW = config.getDirectWeight() != null ? config.getDirectWeight().doubleValue() / 100.0 : 0.80;
            double indirectW = config.getIndirectWeight() != null ? config.getIndirectWeight().doubleValue() / 100.0 : 0.20;

            double combinedScore = (directLevel * directW) + (indirectLevel * indirectW);
            BigDecimal roundedAttainment = BigDecimal.valueOf(combinedScore).setScale(2, RoundingMode.HALF_UP);

            sumCoAttainment = sumCoAttainment.add(roundedAttainment);
            countCOs++;

            BigDecimal target = co.getTargetLevel() != null ? co.getTargetLevel() : new BigDecimal("2.50");
            boolean targetMet = roundedAttainment.compareTo(target) >= 0;

            Map<String, Object> coRes = new LinkedHashMap<>();
            coRes.put("coCode", coCode);
            coRes.put("statement", statement);
            coRes.put("directPct", directPct);
            coRes.put("directLevel", directLevel);
            coRes.put("directScore", BigDecimal.valueOf(directLevel).setScale(2, RoundingMode.HALF_UP));
            coRes.put("indirectPct", indirectPct);
            coRes.put("indirectLevel", indirectLevel);
            coRes.put("indirectScore", indirectScore);
            coRes.put("combinedAttainment", roundedAttainment);
            coRes.put("finalAttainment", roundedAttainment);
            coRes.put("target", target);
            coRes.put("targetMet", targetMet);

            coResults.add(coRes);
        }

        BigDecimal overallCoAttainment = countCOs > 0
                ? sumCoAttainment.divide(BigDecimal.valueOf(countCOs), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double avgDirect = coResults.stream().mapToDouble(c -> ((Number) c.get("directLevel")).doubleValue()).average().orElse(0.0);
        double avgIndirect = coResults.stream().mapToDouble(c -> ((Number) c.get("indirectLevel")).doubleValue()).average().orElse(0.0);

        BigDecimal directAttainment = BigDecimal.valueOf(avgDirect).setScale(2, RoundingMode.HALF_UP);
        BigDecimal indirectAttainment = BigDecimal.valueOf(avgIndirect).setScale(2, RoundingMode.HALF_UP);

        // Calculate Authoritative Course PO and PSO Direct Attainments
        List<String> coIds = cos.stream().map(CourseOutcome::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<CoPoMapping> poMappings = (coPoMappingRepository != null && !coIds.isEmpty())
                ? coPoMappingRepository.findByCourseOutcomeIdIn(coIds)
                : Collections.emptyList();
        Map<String, List<Integer>> poVals = new LinkedHashMap<>();
        for (CoPoMapping m : poMappings) {
            if (m.getMappingLevel() != null && m.getMappingLevel() > 0 && m.getPoCode() != null) {
                poVals.computeIfAbsent(m.getPoCode().toUpperCase(), k -> new ArrayList<>()).add(m.getMappingLevel());
            }
        }
        Map<String, BigDecimal> poAttainment = new LinkedHashMap<>();
        Map<String, BigDecimal> poAverages = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : poVals.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            poAverages.put(e.getKey(), BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            BigDecimal att = BigDecimal.valueOf((avg * overallCoAttainment.doubleValue()) / 3.0).setScale(2, RoundingMode.HALF_UP);
            poAttainment.put(e.getKey(), att);
        }

        List<CoPsoMapping> psoMappings = (coPsoMappingRepository != null && !coIds.isEmpty())
                ? coPsoMappingRepository.findByCourseOutcomeIdIn(coIds)
                : Collections.emptyList();
        Map<String, List<Integer>> psoVals = new LinkedHashMap<>();
        for (CoPsoMapping m : psoMappings) {
            if (m.getMappingLevel() != null && m.getMappingLevel() > 0 && m.getPsoCode() != null) {
                psoVals.computeIfAbsent(m.getPsoCode().toUpperCase(), k -> new ArrayList<>()).add(m.getMappingLevel());
            }
        }
        Map<String, BigDecimal> psoAttainment = new LinkedHashMap<>();
        Map<String, BigDecimal> psoAverages = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : psoVals.entrySet()) {
            double avg = e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            psoAverages.put(e.getKey(), BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            BigDecimal att = BigDecimal.valueOf((avg * overallCoAttainment.doubleValue()) / 3.0).setScale(2, RoundingMode.HALF_UP);
            psoAttainment.put(e.getKey(), att);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("programmeBatchCourseId", offeringId);
        response.put("masterCourseId", masterCourseId);
        response.put("config", config);
        response.put("directAttainment", directAttainment);
        response.put("indirectAttainment", indirectAttainment);
        response.put("overallCoAttainment", overallCoAttainment);
        response.put("overallCOAttainment", overallCoAttainment);
        response.put("coAttainments", coResults);
        response.put("coDetails", coResults);
        response.put("poAttainment", poAttainment);
        response.put("psoAttainment", psoAttainment);
        response.put("poDirectAttainment", poAttainment);
        response.put("psoDirectAttainment", psoAttainment);
        response.put("poAverages", poAverages);
        response.put("psoAverages", psoAverages);
        response.put("examDetails", examResult);
        response.put("surveyDetails", surveyResult);

        return response;
    }

    public List<UploadedDocument> getUploadedDocumentsForOffering(String programmeBatchCourseId) {
        log.debug("[AttainmentCalculationService] getUploadedDocumentsForOffering called | programmeBatchCourseId: " + programmeBatchCourseId);
        return uploadedDocumentRepository.findByProgrammeBatchCourseId(programmeBatchCourseId);
    }

    public List<UploadedDocument> getUploadedDocumentsForCourse(String masterCourseId) {
        log.debug("[AttainmentCalculationService] getUploadedDocumentsForCourse called | masterCourseId: " + masterCourseId);
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
        List<UploadedDocument> docs = new ArrayList<>();
        for (ProgrammeBatchCourse o : offerings) {
            docs.addAll(uploadedDocumentRepository.findByProgrammeBatchCourseId(o.getId()));
        }
        return docs;
    }

    // =========================================================================
    //  PROGRAMME LEVEL INDIRECT SURVEY PROCESSING
    // =========================================================================

    private Sheet findProgrammeSurveySheet(Workbook workbook) {
        int numberOfSheets = workbook.getNumberOfSheets();
        for (int i = 0; i < numberOfSheets; i++) {
            String sheetName = workbook.getSheetName(i).trim();
            if (sheetName.matches("(?i).*average\\s*attainment.*id.*|.*indirect.*|.*programme.*survey.*|.*exit.*survey.*|.*po.*pso.*survey.*")) {
                return workbook.getSheetAt(i);
            }
        }
        // Content inspection fallback
        for (int i = 0; i < numberOfSheets; i++) {
            Sheet s = workbook.getSheetAt(i);
            for (Row r : s) {
                for (Cell c : r) {
                    String text = getStringCellValue(c, null);
                    if (text != null && text.matches("(?i)^PO\\s*1$|^PSO\\s*1$")) {
                        return s;
                    }
                }
            }
        }
        return workbook.getSheetAt(0);
    }
    private String normalizeOutcomeCode(String text) {
        if (text == null || text.isBlank()) return null;
        String raw = text.trim().toUpperCase().replaceAll("\\s+", "");
        if (raw.matches("^(PO|PSO|CO)\\d+$")) {
            return raw;
        }
        String clean = raw.replaceAll("[^A-Z0-9]", "");
        java.util.regex.Matcher mCo = java.util.regex.Pattern.compile("^(CO\\d+)").matcher(clean);
        if (mCo.find()) {
            return mCo.group(1);
        }
        java.util.regex.Matcher mPo = java.util.regex.Pattern.compile("^(PO\\d+)").matcher(clean);
        if (mPo.find()) {
            return mPo.group(1);
        }
        java.util.regex.Matcher mPso = java.util.regex.Pattern.compile("^(PSO\\d+)").matcher(clean);
        if (mPso.find()) {
            return mPso.group(1);
        }
        return null;
    }

    @Transactional
    public ProgrammeSurveyResultDto processAndSaveProgrammeSurveyFile(String masterProgrammeId, String programmeBatchId, MultipartFile file, String uploadedBy) {
        log.debug("[AttainmentCalculationService] processAndSaveProgrammeSurveyFile called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId != null) {
            batchLifecycleService.enforceBatchEditability(programmeBatchId);
        }
        String key = masterProgrammeId + "::" + programmeBatchId;

        // Load authoritative configured POs and PSOs for the programme/batch
        List<ProgrammeOutcome> pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        if (pos == null || pos.isEmpty()) {
            if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
                pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(masterProgrammeId);
            }
            if (pos == null || pos.isEmpty()) {
                List<ProgrammeBatch> otherBatches = programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);
                for (ProgrammeBatch ob : otherBatches) {
                    pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(ob.getId());
                    if (pos != null && !pos.isEmpty()) break;
                }
            }
        }
        if (pos == null || pos.isEmpty()) {
            List<ProgrammeOutcome> defaultPos = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                defaultPos.add(ProgrammeOutcome.builder()
                        .id("po-def-" + i)
                        .programmeBatchId(programmeBatchId)
                        .code("PO" + i)
                        .statement("Program Outcome " + i)
                        .build());
            }
            pos = defaultPos;
        }
        List<ProgrammeSpecificOutcome> psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        if (psos == null || psos.isEmpty()) {
            if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
                psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(masterProgrammeId);
            }
            if (psos == null || psos.isEmpty()) {
                List<ProgrammeBatch> otherBatches = programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);
                for (ProgrammeBatch ob : otherBatches) {
                    psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(ob.getId());
                    if (psos != null && !psos.isEmpty()) break;
                }
            }
        }
        if (psos == null || psos.isEmpty()) {
            List<ProgrammeSpecificOutcome> defaultPsos = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                defaultPsos.add(ProgrammeSpecificOutcome.builder()
                        .id("pso-def-" + i)
                        .programmeBatchId(programmeBatchId)
                        .code("PSO" + i)
                        .statement("Program Specific Outcome " + i)
                        .build());
            }
            psos = defaultPsos;
        }

        Set<String> configuredPOCodes = pos.stream()
                .map(p -> normalizeOutcomeCode(p.getCode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> configuredPSOCodes = psos.stream()
                .map(p -> normalizeOutcomeCode(p.getCode()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ProgrammeSurveyResultDto.PoIndirectItem> poItems = new ArrayList<>();
        List<ProgrammeSurveyResultDto.PsoIndirectItem> psoItems = new ArrayList<>();
        List<ProgrammeSurveyResultDto.StudentSurveyResponseRow> studentResponses = new ArrayList<>();
        int rowsProcessed = 0;

        if (file != null && !file.isEmpty()) {
            Path targetFilePath = null;
            try {
                targetFilePath = saveProgrammeSurveyUploadedFile(file, masterProgrammeId, programmeBatchId);
                String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "programme_survey.xlsx";
                String savedFileName = targetFilePath.getFileName().toString();
                String savedPath = targetFilePath.toAbsolutePath().toString();
                try (InputStream is = Files.newInputStream(targetFilePath);
                     Workbook workbook = WorkbookFactory.create(is)) {
                    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    Sheet sheet = findProgrammeSurveySheet(workbook);

                    // 1. Locate PO/PSO header row
                    int headerRowNum = -1;
                    Map<Integer, String> poColMap = new LinkedHashMap<>();
                    Map<Integer, String> psoColMap = new LinkedHashMap<>();
                    Set<String> duplicateCodes = new LinkedHashSet<>();
                    Set<String> unknownCodes = new LinkedHashSet<>();

                    for (Row row : sheet) {
                        Map<Integer, String> rowPoCols = new LinkedHashMap<>();
                        Map<Integer, String> rowPsoCols = new LinkedHashMap<>();
                        Set<String> rowSeen = new HashSet<>();
                        Set<String> rowDups = new LinkedHashSet<>();
                        Set<String> rowUnk = new LinkedHashSet<>();

                        for (Cell cell : row) {
                            String text = getStringCellValue(cell, evaluator);
                            if (text == null || text.isBlank()) continue;
                            String clean = normalizeOutcomeCode(text);
                            if (clean == null) continue;

                            if (clean.matches("^PO\\d+$")) {
                                if (!rowSeen.add(clean)) rowDups.add(clean);
                                if (!configuredPOCodes.contains(clean)) rowUnk.add(clean);
                                rowPoCols.put(cell.getColumnIndex(), clean);
                            } else if (clean.matches("^PSO\\d+$")) {
                                if (!rowSeen.add(clean)) rowDups.add(clean);
                                if (!configuredPSOCodes.contains(clean)) rowUnk.add(clean);
                                rowPsoCols.put(cell.getColumnIndex(), clean);
                            }
                        }

                        if ((rowPoCols.size() + rowPsoCols.size()) > (poColMap.size() + psoColMap.size())) {
                            poColMap = rowPoCols;
                            psoColMap = rowPsoCols;
                            duplicateCodes = rowDups;
                            unknownCodes = rowUnk;
                            headerRowNum = row.getRowNum();
                        }
                    }

                    if (headerRowNum == -1 || (poColMap.isEmpty() && psoColMap.isEmpty())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Program Outcome (PO/PSO) headers found in Programme End Survey sheet.");
                    }

                    // 2. Strict Exact Outcome Validation Rules
                    // Duplicate Check (A6)
                    if (!duplicateCodes.isEmpty()) {
                        String dup = duplicateCodes.iterator().next();
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme End Survey contains duplicate outcome " + dup + ".");
                    }

                    // Extra / Unknown Outcome Check (A4 & A5)
                    if (!unknownCodes.isEmpty()) {
                        String unk = unknownCodes.iterator().next();
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme End Survey contains unconfigured outcome " + unk + ".");
                    }

                    // Missing PO Check (A3 & A7)
                    for (String cfgPo : configuredPOCodes) {
                        if (!poColMap.containsValue(cfgPo)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme End Survey is missing configured outcome " + cfgPo + ".");
                        }
                    }

                    // Missing PSO Check (A3 & A7)
                    for (String cfgPso : configuredPSOCodes) {
                        if (!psoColMap.containsValue(cfgPso)) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Programme End Survey is missing configured outcome " + cfgPso + ".");
                        }
                    }

                    // 3. Dynamic Student Response Rows Processing (A9)
                    int prnColIdx = -1;
                    int nameColIdx = -1;
                    int srNoColIdx = -1;
                    Row headerRow = sheet.getRow(headerRowNum);
                    if (headerRow != null) {
                        for (Cell cell : headerRow) {
                            String hText = getStringCellValue(cell, evaluator);
                            if (hText != null) {
                                String clean = hText.toLowerCase().replaceAll("[^a-z]", "");
                                if (clean.contains("prn") || clean.contains("rollno") || clean.contains("studentid")) {
                                    prnColIdx = cell.getColumnIndex();
                                } else if (clean.contains("name") || clean.contains("studentname")) {
                                    nameColIdx = cell.getColumnIndex();
                                } else if (clean.contains("srno") || clean.contains("sno")) {
                                    srNoColIdx = cell.getColumnIndex();
                                }
                            }
                        }
                    }

                    Map<String, List<Double>> poRatingsMap = new LinkedHashMap<>();
                    Map<String, List<Double>> psoRatingsMap = new LinkedHashMap<>();
                    for (String po : configuredPOCodes) poRatingsMap.put(po, new ArrayList<>());
                    for (String pso : configuredPSOCodes) psoRatingsMap.put(pso, new ArrayList<>());

                    for (int r = headerRowNum + 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        boolean hasData = false;
                        Map<String, Double> rowPoVals = new HashMap<>();
                        Map<String, Double> rowPsoVals = new HashMap<>();
                        Map<String, String> rowPoFeedbacks = new LinkedHashMap<>();
                        Map<String, String> rowPsoFeedbacks = new LinkedHashMap<>();

                        for (Map.Entry<Integer, String> entry : poColMap.entrySet()) {
                            Cell c = row.getCell(entry.getKey());
                            Double num = getNumericCellValue(c, evaluator);
                            String feedback = "Substantial";
                            if (num == null) {
                                String s = getStringCellValue(c, evaluator);
                                if (s != null && !s.isBlank()) {
                                    String tr = s.trim().toLowerCase();
                                    if (tr.contains("substantial") || tr.equals("3") || tr.equals("3.0")) {
                                        num = 3.0; feedback = "Substantial";
                                    } else if (tr.contains("moderate") || tr.equals("2") || tr.equals("2.0")) {
                                        num = 2.0; feedback = "Moderate";
                                    } else if (tr.contains("slight") || tr.equals("1") || tr.equals("1.0")) {
                                        num = 1.0; feedback = "Slight";
                                    }
                                }
                            } else {
                                int v = (int) Math.round(num);
                                feedback = v == 3 ? "Substantial" : (v == 2 ? "Moderate" : "Slight");
                            }
                            if (num != null && num >= 0) {
                                rowPoVals.put(entry.getValue(), Math.min(3.0, num));
                                rowPoFeedbacks.put(entry.getValue(), feedback);
                                hasData = true;
                            }
                        }

                        for (Map.Entry<Integer, String> entry : psoColMap.entrySet()) {
                            Cell c = row.getCell(entry.getKey());
                            Double num = getNumericCellValue(c, evaluator);
                            String feedback = "Substantial";
                            if (num == null) {
                                String s = getStringCellValue(c, evaluator);
                                if (s != null && !s.isBlank()) {
                                    String tr = s.trim().toLowerCase();
                                    if (tr.contains("substantial") || tr.equals("3") || tr.equals("3.0")) {
                                        num = 3.0; feedback = "Substantial";
                                    } else if (tr.contains("moderate") || tr.equals("2") || tr.equals("2.0")) {
                                        num = 2.0; feedback = "Moderate";
                                    } else if (tr.contains("slight") || tr.equals("1") || tr.equals("1.0")) {
                                        num = 1.0; feedback = "Slight";
                                    }
                                }
                            } else {
                                int v = (int) Math.round(num);
                                feedback = v == 3 ? "Substantial" : (v == 2 ? "Moderate" : "Slight");
                            }
                            if (num != null && num >= 0) {
                                rowPsoVals.put(entry.getValue(), Math.min(3.0, num));
                                rowPsoFeedbacks.put(entry.getValue(), feedback);
                                hasData = true;
                            }
                        }

                        if (hasData) {
                            rowsProcessed++;
                            for (Map.Entry<String, Double> e : rowPoVals.entrySet()) {
                                poRatingsMap.get(e.getKey()).add(e.getValue());
                            }
                            for (Map.Entry<String, Double> e : rowPsoVals.entrySet()) {
                                psoRatingsMap.get(e.getKey()).add(e.getValue());
                            }

                            String prn = null;
                            if (prnColIdx != -1) {
                                String p = getStringCellValue(row.getCell(prnColIdx), evaluator);
                                if (p != null && !p.isBlank()) prn = p.trim();
                            }
                            String sName = null;
                            if (nameColIdx != -1) {
                                String n = getStringCellValue(row.getCell(nameColIdx), evaluator);
                                if (n != null && !n.isBlank()) sName = n.trim();
                            }
                            if (prn == null || prn.isBlank()) {
                                prn = "PRN-" + (studentResponses.size() + 1);
                            }
                            if (sName == null || sName.isBlank()) {
                                sName = "Student " + prn;
                            }
                            studentResponses.add(ProgrammeSurveyResultDto.StudentSurveyResponseRow.builder()
                                    .srNo(studentResponses.size() + 1)
                                    .prn(prn)
                                    .studentName(sName)
                                    .poRatings(rowPoFeedbacks)
                                    .psoRatings(rowPsoFeedbacks)
                                    .build());
                        }
                    }

                    if (rowsProcessed == 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid survey response rows found in the Programme End Survey sheet.");
                    }

                    // Compute weighted percentage and range-based indirect attainment (1-3) for each PO & PSO:
                    // totalPct = (pct1 * 0.33) + (pct2 * 0.67) + (pct3 * 1.0)
                    // Level bands: 0-40% -> 1.0, 40-70% -> 2.0, 70-100% -> 3.0
                    for (String poCode : configuredPOCodes) {
                        List<Double> ratings = poRatingsMap.get(poCode);
                        BigDecimal attainmentVal;
                        if (ratings != null && !ratings.isEmpty()) {
                            int count1 = 0, count2 = 0, count3 = 0;
                            for (Double r : ratings) {
                                if (r == null) continue;
                                long rounded = Math.round(r);
                                if (rounded <= 1) count1++;
                                else if (rounded == 2) count2++;
                                else count3++;
                            }
                            int total = count1 + count2 + count3;
                            double pct1 = total > 0 ? ((double) count1 * 100.0 / total) : 0.0;
                            double pct2 = total > 0 ? ((double) count2 * 100.0 / total) : 0.0;
                            double pct3 = total > 0 ? ((double) count3 * 100.0 / total) : 0.0;

                            double totalPct = (pct1 * 0.33) + (pct2 * 0.67) + (pct3 * 1.0);

                            int level;
                            if (totalPct > 70.0) {
                                level = 3;
                            } else if (totalPct > 40.0) {
                                level = 2;
                            } else if (totalPct > 0.0) {
                                level = 1;
                            } else {
                                level = 0;
                            }
                            attainmentVal = BigDecimal.valueOf(level).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            attainmentVal = BigDecimal.ZERO;
                        }
                        poItems.add(ProgrammeSurveyResultDto.PoIndirectItem.builder()
                                .poCode(poCode)
                                .indirectAttainment(attainmentVal)
                                .build());
                    }

                    for (String psoCode : configuredPSOCodes) {
                        List<Double> ratings = psoRatingsMap.get(psoCode);
                        BigDecimal attainmentVal;
                        if (ratings != null && !ratings.isEmpty()) {
                            int count1 = 0, count2 = 0, count3 = 0;
                            for (Double r : ratings) {
                                if (r == null) continue;
                                long rounded = Math.round(r);
                                if (rounded <= 1) count1++;
                                else if (rounded == 2) count2++;
                                else count3++;
                            }
                            int total = count1 + count2 + count3;
                            double pct1 = total > 0 ? ((double) count1 * 100.0 / total) : 0.0;
                            double pct2 = total > 0 ? ((double) count2 * 100.0 / total) : 0.0;
                            double pct3 = total > 0 ? ((double) count3 * 100.0 / total) : 0.0;

                            double totalPct = (pct1 * 0.33) + (pct2 * 0.67) + (pct3 * 1.0);

                            int level;
                            if (totalPct > 70.0) {
                                level = 3;
                            } else if (totalPct > 40.0) {
                                level = 2;
                            } else if (totalPct > 0.0) {
                                level = 1;
                            } else {
                                level = 0;
                            }
                            attainmentVal = BigDecimal.valueOf(level).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            attainmentVal = BigDecimal.ZERO;
                        }
                        psoItems.add(ProgrammeSurveyResultDto.PsoIndirectItem.builder()
                                .psoCode(psoCode)
                                .indirectAttainment(attainmentVal)
                                .build());
                    }

                    // 4. Persist uploaded document record only after successful validation
                    UploadedDocument doc = UploadedDocument.builder()
                            .id("doc-" + UUID.randomUUID().toString().substring(0, 8))
                            .programmeBatchId(programmeBatchId)
                            .documentType(DocumentType.SURVEY)
                            .fileName(originalFileName)
                            .savedFileName(savedFileName)
                            .savedPath(savedPath)
                            .fileSize(file.getSize())
                            .recordsProcessed(rowsProcessed)
                            .uploadedBy(uploadedBy != null ? uploadedBy : "Programme Coordinator")
                            .uploadedAt(ZonedDateTime.now())
                            .build();
                    uploadedDocumentRepository.save(doc);
                }
            } catch (ResponseStatusException rse) {
                if (targetFilePath != null && Files.exists(targetFilePath)) {
                    try { Files.deleteIfExists(targetFilePath); } catch (Exception ignored) {}
                }
                throw rse;
            } catch (Exception e) {
                if (targetFilePath != null && Files.exists(targetFilePath)) {
                    try { Files.deleteIfExists(targetFilePath); } catch (Exception ignored) {}
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse Programme End Survey file: " + e.getMessage());
            }
        } else {
            // When no survey file is uploaded yet, initialize zero attainment for configured outcomes
            for (String poCode : configuredPOCodes) {
                poItems.add(ProgrammeSurveyResultDto.PoIndirectItem.builder().poCode(poCode).indirectAttainment(BigDecimal.ZERO).build());
            }
            for (String psoCode : configuredPSOCodes) {
                psoItems.add(ProgrammeSurveyResultDto.PsoIndirectItem.builder().psoCode(psoCode).indirectAttainment(BigDecimal.ZERO).build());
            }
        }

        ProgrammeSurveyResultDto result = ProgrammeSurveyResultDto.builder()
                .uploadId("psurvey-" + UUID.randomUUID().toString().substring(0, 8))
                .masterProgrammeId(masterProgrammeId)
                .programmeBatchId(programmeBatchId)
                .surveyType("PROGRAMME_INDIRECT")
                .recordsProcessed(rowsProcessed)
                .poIndirectAttainment(poItems)
                .psoIndirectAttainment(psoItems)
                .studentSurveyResponses(studentResponses)
                .status("PROCESSED")
                .build();

        programmeSurveyStore.put(key, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ProgrammeSurveyResultDto getProgrammeSurveyResult(String masterProgrammeId, String programmeBatchId) {
        log.debug("[AttainmentCalculationService] getProgrammeSurveyResult called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        String key = masterProgrammeId + "::" + programmeBatchId;
        if (programmeSurveyStore.containsKey(key)) {
            return programmeSurveyStore.get(key);
        }

        // Check if there is an existing uploaded document on disk
        List<UploadedDocument> docs = uploadedDocumentRepository.findAll().stream()
                .filter(d -> programmeBatchId.equalsIgnoreCase(d.getProgrammeBatchId()) && d.getDocumentType() == DocumentType.SURVEY)
                .sorted(Comparator.comparing(UploadedDocument::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (!docs.isEmpty()) {
            UploadedDocument latestDoc = docs.get(0);
            if (latestDoc.getSavedPath() != null) {
                Path p = Paths.get(latestDoc.getSavedPath());
                if (Files.exists(p)) {
                    try {
                        byte[] bytes = Files.readAllBytes(p);
                        ByteArrayMultipartFile mockFile = new ByteArrayMultipartFile(
                                "file", latestDoc.getFileName() != null ? latestDoc.getFileName() : "programme_survey.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
                        return processAndSaveProgrammeSurveyFile(masterProgrammeId, programmeBatchId, mockFile, latestDoc.getUploadedBy());
                    } catch (Exception ignored) {}
                }
            }
        }

        return processAndSaveProgrammeSurveyFile(masterProgrammeId, programmeBatchId, null, null);
    }

    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException { Files.write(dest.toPath(), content); }
    }

    @Transactional
    public ProgrammeSurveyResultDto saveProgrammeSurveyResult(String masterProgrammeId, String programmeBatchId, ProgrammeSurveyResultDto payload) {
        log.debug("[AttainmentCalculationService] saveProgrammeSurveyResult called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId != null) {
            batchLifecycleService.enforceBatchEditability(programmeBatchId);
        }
        String key = masterProgrammeId + "::" + programmeBatchId;
        if (payload != null) {
            payload.setMasterProgrammeId(masterProgrammeId);
            payload.setProgrammeBatchId(programmeBatchId);
            if (payload.getUploadId() == null || payload.getUploadId().isBlank()) {
                payload.setUploadId("psurvey-" + UUID.randomUUID().toString().substring(0, 8));
            }
            payload.setStatus("SAVED");
            programmeSurveyStore.put(key, payload);
            return payload;
        }
        return getProgrammeSurveyResult(masterProgrammeId, programmeBatchId);
    }

    @Transactional
    public void deleteProgrammeSurvey(String masterProgrammeId, String programmeBatchId) {
        log.debug("[AttainmentCalculationService] deleteProgrammeSurvey called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId != null) {
            batchLifecycleService.enforceBatchEditability(programmeBatchId);
        }
        String key = masterProgrammeId + "::" + programmeBatchId;
        programmeSurveyStore.remove(key);

        List<UploadedDocument> docs = uploadedDocumentRepository.findAll().stream()
                .filter(d -> programmeBatchId.equalsIgnoreCase(d.getProgrammeBatchId()) && d.getDocumentType() == DocumentType.SURVEY)
                .toList();

        for (UploadedDocument doc : docs) {
            if (doc.getSavedPath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(doc.getSavedPath()));
                } catch (Exception ignored) {}
            }
            uploadedDocumentRepository.delete(doc);
        }
    }

    // =========================================================================
    //  PROGRAMME ATTAINMENT AGGREGATION ENGINE (BATCH-CENTRIC)
    // =========================================================================

    @Transactional(readOnly = true)
    public ProgrammeAttainmentResultDto calculateProgrammeAttainment(String masterProgrammeId, String programmeBatchId) {
        log.debug("[AttainmentCalculationService] calculateProgrammeAttainment called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        MasterProgramme prog = masterProgrammeRepository.findById(masterProgrammeId).orElse(null);
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId).orElse(null);

        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
        List<ProgrammeOutcome> pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        if (pos == null || pos.isEmpty()) {
            pos = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(masterProgrammeId);
        }
        if (pos == null || pos.isEmpty()) {
            List<ProgrammeOutcome> defaultPos = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                defaultPos.add(ProgrammeOutcome.builder()
                        .id("po-def-" + i)
                        .programmeBatchId(programmeBatchId)
                        .code("PO" + i)
                        .statement("Engineering Knowledge and Competency PO" + i)
                        .build());
            }
            pos = defaultPos;
        }
        List<ProgrammeSpecificOutcome> psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId);
        if (psos == null || psos.isEmpty()) {
            psos = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(masterProgrammeId);
        }
        if (psos == null || psos.isEmpty()) {
            List<ProgrammeSpecificOutcome> defaultPsos = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                defaultPsos.add(ProgrammeSpecificOutcome.builder()
                        .id("pso-def-" + i)
                        .programmeBatchId(programmeBatchId)
                        .code("PSO" + i)
                        .statement("Programme Specific Competency PSO" + i)
                        .build());
            }
            psos = defaultPsos;
        }

        Map<String, String> poStatementMap = new HashMap<>();
        Map<String, BigDecimal> targetMap = new HashMap<>();
        for (ProgrammeOutcome po : pos) {
            if (po.getCode() != null) {
                if (po.getStatement() != null && !po.getStatement().isBlank()) {
                    poStatementMap.put(po.getCode().toUpperCase(), po.getStatement());
                }
                if (po.getTarget() != null) {
                    targetMap.put(po.getCode().toUpperCase(), po.getTarget());
                }
            }
        }
        Map<String, String> psoStatementMap = new HashMap<>();
        for (ProgrammeSpecificOutcome pso : psos) {
            if (pso.getCode() != null) {
                if (pso.getStatement() != null && !pso.getStatement().isBlank()) {
                    psoStatementMap.put(pso.getCode().toUpperCase(), pso.getStatement());
                }
                if (pso.getTarget() != null) {
                    targetMap.put(pso.getCode().toUpperCase(), pso.getTarget());
                }
            }
        }

        int duration = (prog != null && prog.getDurationYears() != null && prog.getDurationYears() > 0) ? prog.getDurationYears() : 4;
        int maxSem = duration * 2;
        if (!offerings.isEmpty()) {
            int maxOfferingSem = offerings.stream()
                    .map(ProgrammeBatchCourse::getSemester)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(maxSem);
            if (maxOfferingSem > maxSem) {
                maxSem = maxOfferingSem;
            }
        }

        Map<Integer, List<ProgrammeBatchCourse>> semOfferings = new TreeMap<>();
        for (ProgrammeBatchCourse o : offerings) {
            int sem = o.getSemester() != null ? o.getSemester() : 1;
            semOfferings.computeIfAbsent(sem, k -> new ArrayList<>()).add(o);
        }

        List<ProgrammeAttainmentResultDto.CourseContributionRow> courseMappingRows = new ArrayList<>();
        List<ProgrammeAttainmentResultDto.CourseContributionRow> courseDirectAttainmentRows = new ArrayList<>();

        for (ProgrammeBatchCourse off : offerings) {
            String courseCode = (off.getEffectiveCourseCode() != null && !off.getEffectiveCourseCode().isBlank())
                    ? off.getEffectiveCourseCode()
                    : off.getId();
            String courseName = (off.getEffectiveCourseName() != null && !off.getEffectiveCourseName().isBlank())
                    ? off.getEffectiveCourseName()
                    : "Course " + courseCode;
            String coordinatorName = (off.getCourseCoordinatorName() != null && !off.getCourseCoordinatorName().isBlank())
                    ? off.getCourseCoordinatorName()
                    : ((off.getAssignedFaculty() != null && !off.getAssignedFaculty().isBlank())
                    ? off.getAssignedFaculty()
                    : (off.getCourseCoordinatorId() != null ? String.valueOf(off.getCourseCoordinatorId()) : ""));
            boolean isLab = (courseName != null && courseName.toLowerCase().contains("lab"))
                    || (courseCode != null && courseCode.toLowerCase().contains("lab"))
                    || (off.getCourseType() != null && off.getCourseType().equalsIgnoreCase("LAB"));

            Map<String, Object> cAtt = null;
            try {
                cAtt = calculateCourseCoAttainment(off.getId());
            } catch (Exception ignored) {}

            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> poAvgMap = (cAtt != null && cAtt.get("poAverages") instanceof Map) ? (Map<String, BigDecimal>) cAtt.get("poAverages") : Collections.emptyMap();
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> psoAvgMap = (cAtt != null && cAtt.get("psoAverages") instanceof Map) ? (Map<String, BigDecimal>) cAtt.get("psoAverages") : Collections.emptyMap();
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> poAttMap = (cAtt != null && cAtt.get("poAttainment") instanceof Map) ? (Map<String, BigDecimal>) cAtt.get("poAttainment") : Collections.emptyMap();
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> psoAttMap = (cAtt != null && cAtt.get("psoAttainment") instanceof Map) ? (Map<String, BigDecimal>) cAtt.get("psoAttainment") : Collections.emptyMap();

            Map<String, BigDecimal> courseMappingPoMap = new LinkedHashMap<>();
            Map<String, BigDecimal> courseDirectPoMap = new LinkedHashMap<>();
            for (ProgrammeOutcome po : pos) {
                String code = po.getCode().toUpperCase();
                courseMappingPoMap.put(code, poAvgMap.getOrDefault(code, null));
                courseDirectPoMap.put(code, poAttMap.getOrDefault(code, null));
            }

            Map<String, BigDecimal> courseMappingPsoMap = new LinkedHashMap<>();
            Map<String, BigDecimal> courseDirectPsoMap = new LinkedHashMap<>();
            for (ProgrammeSpecificOutcome pso : psos) {
                String code = pso.getCode().toUpperCase();
                courseMappingPsoMap.put(code, psoAvgMap.getOrDefault(code, null));
                courseDirectPsoMap.put(code, psoAttMap.getOrDefault(code, null));
            }

            courseMappingRows.add(ProgrammeAttainmentResultDto.CourseContributionRow.builder()
                    .programmeBatchCourseId(off.getId())
                    .masterCourseId(off.getMasterCourseId())
                    .semester(off.getSemester() != null ? off.getSemester() : 1)
                    .courseCode(courseCode)
                    .courseName(courseName)
                    .resourceName(coordinatorName)
                    .courseNo(courseCode)
                    .isLab(isLab)
                    .poValues(courseMappingPoMap)
                    .psoValues(courseMappingPsoMap)
                    .build());

            courseDirectAttainmentRows.add(ProgrammeAttainmentResultDto.CourseContributionRow.builder()
                    .programmeBatchCourseId(off.getId())
                    .masterCourseId(off.getMasterCourseId())
                    .semester(off.getSemester() != null ? off.getSemester() : 1)
                    .courseCode(courseCode)
                    .courseName(courseName)
                    .resourceName(coordinatorName)
                    .courseNo(courseCode)
                    .isLab(isLab)
                    .poValues(courseDirectPoMap)
                    .psoValues(courseDirectPsoMap)
                    .build());
        }

        courseMappingRows.sort(Comparator.comparing((ProgrammeAttainmentResultDto.CourseContributionRow r) -> r.getSemester() != null ? r.getSemester() : 1)
                .thenComparing(r -> r.getCourseCode() != null ? r.getCourseCode() : ""));
        courseDirectAttainmentRows.sort(Comparator.comparing((ProgrammeAttainmentResultDto.CourseContributionRow r) -> r.getSemester() != null ? r.getSemester() : 1)
                .thenComparing(r -> r.getCourseCode() != null ? r.getCourseCode() : ""));

        List<ProgrammeAttainmentResultDto.OutcomeMappingItem> poMappingBreakdown = new ArrayList<>();
        List<ProgrammeAttainmentResultDto.OutcomeDirectItem> poDirectBreakdown = new ArrayList<>();

        for (int i = 0; i < pos.size(); i++) {
            String poCode = pos.get(i).getCode();
            List<ProgrammeAttainmentResultDto.SemesterValue> semMapValues = new ArrayList<>();
            List<ProgrammeAttainmentResultDto.SemesterValue> semDirectValues = new ArrayList<>();

            double batchAllCoursesMapSum = 0;
            int batchAllCoursesMapCount = 0;
            double batchAllCoursesDirectSum = 0;
            int batchAllCoursesDirectCount = 0;

            for (int s = 1; s <= maxSem; s++) {
                final int currentSem = s;
                List<ProgrammeAttainmentResultDto.CourseContributionRow> sMapRows = courseMappingRows.stream()
                        .filter(r -> r.getSemester() != null && r.getSemester() == currentSem)
                        .collect(Collectors.toList());
                List<ProgrammeAttainmentResultDto.CourseContributionRow> sDirRows = courseDirectAttainmentRows.stream()
                        .filter(r -> r.getSemester() != null && r.getSemester() == currentSem)
                        .collect(Collectors.toList());

                double semMapSum = 0;
                int semMapCount = 0;
                for (ProgrammeAttainmentResultDto.CourseContributionRow r : sMapRows) {
                    BigDecimal val = (r.getPoValues() != null) ? r.getPoValues().get(poCode.toUpperCase()) : null;
                    if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
                        semMapSum += val.doubleValue();
                        semMapCount++;
                        batchAllCoursesMapSum += val.doubleValue();
                        batchAllCoursesMapCount++;
                    }
                }

                double semDirectSum = 0;
                int semDirectCount = 0;
                for (ProgrammeAttainmentResultDto.CourseContributionRow r : sDirRows) {
                    BigDecimal val = (r.getPoValues() != null) ? r.getPoValues().get(poCode.toUpperCase()) : null;
                    if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
                        semDirectSum += val.doubleValue();
                        semDirectCount++;
                        batchAllCoursesDirectSum += val.doubleValue();
                        batchAllCoursesDirectCount++;
                    }
                }

                BigDecimal mapVal = semMapCount > 0 ? BigDecimal.valueOf(semMapSum / semMapCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal directVal = semDirectCount > 0 ? BigDecimal.valueOf(semDirectSum / semDirectCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                semMapValues.add(ProgrammeAttainmentResultDto.SemesterValue.builder().semester(s).averageMapping(mapVal).averageAttainment(directVal).build());
                semDirectValues.add(ProgrammeAttainmentResultDto.SemesterValue.builder().semester(s).averageMapping(mapVal).averageAttainment(directVal).build());
            }

            BigDecimal avgMap = batchAllCoursesMapCount > 0
                    ? BigDecimal.valueOf(batchAllCoursesMapSum / batchAllCoursesMapCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal avgDirect = batchAllCoursesDirectCount > 0
                    ? BigDecimal.valueOf(batchAllCoursesDirectSum / batchAllCoursesDirectCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            poMappingBreakdown.add(ProgrammeAttainmentResultDto.OutcomeMappingItem.builder()
                    .poCode(poCode)
                    .semesterValues(semMapValues)
                    .overallAverage(avgMap)
                    .build());

            poDirectBreakdown.add(ProgrammeAttainmentResultDto.OutcomeDirectItem.builder()
                    .poCode(poCode)
                    .semesterValues(semDirectValues)
                    .overallAverage(avgDirect)
                    .build());
        }

        List<ProgrammeAttainmentResultDto.OutcomeMappingItem> psoMappingBreakdown = new ArrayList<>();
        List<ProgrammeAttainmentResultDto.OutcomeDirectItem> psoDirectBreakdown = new ArrayList<>();

        for (int i = 0; i < psos.size(); i++) {
            String psoCode = psos.get(i).getCode();
            List<ProgrammeAttainmentResultDto.SemesterValue> semMapValues = new ArrayList<>();
            List<ProgrammeAttainmentResultDto.SemesterValue> semDirectValues = new ArrayList<>();

            double batchAllCoursesMapSum = 0;
            int batchAllCoursesMapCount = 0;
            double batchAllCoursesDirectSum = 0;
            int batchAllCoursesDirectCount = 0;

            for (int s = 1; s <= maxSem; s++) {
                final int currentSem = s;
                List<ProgrammeAttainmentResultDto.CourseContributionRow> sMapRows = courseMappingRows.stream()
                        .filter(r -> r.getSemester() != null && r.getSemester() == currentSem)
                        .collect(Collectors.toList());
                List<ProgrammeAttainmentResultDto.CourseContributionRow> sDirRows = courseDirectAttainmentRows.stream()
                        .filter(r -> r.getSemester() != null && r.getSemester() == currentSem)
                        .collect(Collectors.toList());

                double semMapSum = 0;
                int semMapCount = 0;
                for (ProgrammeAttainmentResultDto.CourseContributionRow r : sMapRows) {
                    BigDecimal val = (r.getPsoValues() != null) ? r.getPsoValues().get(psoCode.toUpperCase()) : null;
                    if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
                        semMapSum += val.doubleValue();
                        semMapCount++;
                        batchAllCoursesMapSum += val.doubleValue();
                        batchAllCoursesMapCount++;
                    }
                }

                double semDirectSum = 0;
                int semDirectCount = 0;
                for (ProgrammeAttainmentResultDto.CourseContributionRow r : sDirRows) {
                    BigDecimal val = (r.getPsoValues() != null) ? r.getPsoValues().get(psoCode.toUpperCase()) : null;
                    if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
                        semDirectSum += val.doubleValue();
                        semDirectCount++;
                        batchAllCoursesDirectSum += val.doubleValue();
                        batchAllCoursesDirectCount++;
                    }
                }

                BigDecimal mapVal = semMapCount > 0 ? BigDecimal.valueOf(semMapSum / semMapCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal directVal = semDirectCount > 0 ? BigDecimal.valueOf(semDirectSum / semDirectCount).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                semMapValues.add(ProgrammeAttainmentResultDto.SemesterValue.builder().semester(s).averageMapping(mapVal).averageAttainment(directVal).build());
                semDirectValues.add(ProgrammeAttainmentResultDto.SemesterValue.builder().semester(s).averageMapping(mapVal).averageAttainment(directVal).build());
            }

            BigDecimal avgMap = batchAllCoursesMapCount > 0
                    ? BigDecimal.valueOf(batchAllCoursesMapSum / batchAllCoursesMapCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal avgDirect = batchAllCoursesDirectCount > 0
                    ? BigDecimal.valueOf(batchAllCoursesDirectSum / batchAllCoursesDirectCount).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            psoMappingBreakdown.add(ProgrammeAttainmentResultDto.OutcomeMappingItem.builder()
                    .psoCode(psoCode)
                    .semesterValues(semMapValues)
                    .overallAverage(avgMap)
                    .build());

            psoDirectBreakdown.add(ProgrammeAttainmentResultDto.OutcomeDirectItem.builder()
                    .psoCode(psoCode)
                    .semesterValues(semDirectValues)
                    .overallAverage(avgDirect)
                    .build());
        }

        String key = masterProgrammeId + "::" + programmeBatchId;
        ProgrammeSurveyResultDto exitSurvey = programmeSurveyStore.containsKey(key)
                ? programmeSurveyStore.get(key)
                : processAndSaveProgrammeSurveyFile(masterProgrammeId, programmeBatchId, null, null);

        Map<String, BigDecimal> exitSurveyPoMap = new HashMap<>();
        if (exitSurvey.getPoIndirectAttainment() != null) {
            for (ProgrammeSurveyResultDto.PoIndirectItem it : exitSurvey.getPoIndirectAttainment()) {
                exitSurveyPoMap.put(it.getPoCode().toUpperCase(), it.getIndirectAttainment());
            }
        }

        Map<String, BigDecimal> exitSurveyPsoMap = new HashMap<>();
        if (exitSurvey.getPsoIndirectAttainment() != null) {
            for (ProgrammeSurveyResultDto.PsoIndirectItem it : exitSurvey.getPsoIndirectAttainment()) {
                exitSurveyPsoMap.put(it.getPsoCode().toUpperCase(), it.getIndirectAttainment());
            }
        }

        // Combine exit survey scores
        Map<String, BigDecimal> combinedExitScores = new HashMap<>(exitSurveyPoMap);
        combinedExitScores.putAll(exitSurveyPsoMap);

        // Compute consolidated indirect scores across all Surveys & Co-Curricular Events + Programme End Survey
        Map<String, BigDecimal> consolidatedIndirectMap = (indirectAssessmentService != null)
                ? indirectAssessmentService.computeConsolidatedScores(programmeBatchId, combinedExitScores)
                : combinedExitScores;

        List<ProgrammeAttainmentResultDto.OutcomeAttainmentItem> poOverallList = new ArrayList<>();
        for (ProgrammeAttainmentResultDto.OutcomeDirectItem d : poDirectBreakdown) {
            String code = d.getPoCode();
            BigDecimal direct = d.getOverallAverage();
            BigDecimal indirect = consolidatedIndirectMap.getOrDefault(code.toUpperCase(), exitSurveyPoMap.getOrDefault(code.toUpperCase(), BigDecimal.ZERO));

            double overallScore = (direct.doubleValue() * 0.80) + (indirect.doubleValue() * 0.20);
            BigDecimal overall = BigDecimal.valueOf(overallScore).setScale(2, RoundingMode.HALF_UP);

            BigDecimal target = targetMap.getOrDefault(code.toUpperCase(), new BigDecimal("2.50"));
            BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? overall.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            boolean achieved = overall.compareTo(target) >= 0;
            String obs = String.format("%s%% Target %s", pct, achieved ? "Achieved" : "Not Achieved");

            List<String> actions = Collections.emptyList();

            String statement = poStatementMap.getOrDefault(code.toUpperCase(), "Programme Outcome " + code);

            poOverallList.add(ProgrammeAttainmentResultDto.OutcomeAttainmentItem.builder()
                    .poCode(code)
                    .outcomeCode(code)
                    .outcomeStatement(statement)
                    .directAttainment(direct)
                    .indirectAttainment(indirect)
                    .overallAttainment(overall)
                    .target(target)
                    .achievementPercentage(pct)
                    .observation(obs)
                    .actions(actions)
                    .build());
        }

        List<ProgrammeAttainmentResultDto.OutcomeAttainmentItem> psoOverallList = new ArrayList<>();
        for (ProgrammeAttainmentResultDto.OutcomeDirectItem d : psoDirectBreakdown) {
            String code = d.getPsoCode();
            BigDecimal direct = d.getOverallAverage();
            BigDecimal indirect = consolidatedIndirectMap.getOrDefault(code.toUpperCase(), exitSurveyPsoMap.getOrDefault(code.toUpperCase(), BigDecimal.ZERO));

            double overallScore = (direct.doubleValue() * 0.80) + (indirect.doubleValue() * 0.20);
            BigDecimal overall = BigDecimal.valueOf(overallScore).setScale(2, RoundingMode.HALF_UP);

            BigDecimal target = targetMap.getOrDefault(code.toUpperCase(), new BigDecimal("2.50"));
            BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? overall.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            boolean achieved = overall.compareTo(target) >= 0;
            String obs = String.format("%s%% Target %s", pct, achieved ? "Achieved" : "Not Achieved");

            List<String> actions = Collections.emptyList();
            String statement = psoStatementMap.getOrDefault(code.toUpperCase(), "Programme Specific Outcome " + code);

            psoOverallList.add(ProgrammeAttainmentResultDto.OutcomeAttainmentItem.builder()
                    .psoCode(code)
                    .outcomeCode(code)
                    .outcomeStatement(statement)
                    .directAttainment(direct)
                    .indirectAttainment(indirect)
                    .overallAttainment(overall)
                    .target(target)
                    .achievementPercentage(pct)
                    .observation(obs)
                    .actions(actions)
                    .build());
        }

        Map<String, BigDecimal> indirectMap = new LinkedHashMap<>(consolidatedIndirectMap);

        List<ProgrammeAttainmentResultDto.StudentSurveyResponseRow> studentRows = new ArrayList<>();
        if (exitSurvey != null && exitSurvey.getStudentSurveyResponses() != null) {
            for (ProgrammeSurveyResultDto.StudentSurveyResponseRow sr : exitSurvey.getStudentSurveyResponses()) {
                studentRows.add(ProgrammeAttainmentResultDto.StudentSurveyResponseRow.builder()
                        .srNo(sr.getSrNo())
                        .prn(sr.getPrn())
                        .studentName(sr.getStudentName())
                        .poRatings(sr.getPoRatings())
                        .psoRatings(sr.getPsoRatings())
                        .build());
            }
        }

        return ProgrammeAttainmentResultDto.builder()
                .programme(prog != null ? ProgrammeAttainmentResultDto.ProgrammeSummary.builder().id(prog.getId()).code(prog.getCode()).name(prog.getName()).build() : null)
                .batch(batch != null ? ProgrammeAttainmentResultDto.BatchSummary.builder().id(batch.getId()).name(batch.getName()).startYear(batch.getStartYear() != null ? String.valueOf(batch.getStartYear()) : "").endYear(batch.getEndYear() != null ? String.valueOf(batch.getEndYear()) : "").build() : null)
                .summary(ProgrammeAttainmentResultDto.Summary.builder().courseOfferingCount(offerings.size()).semesterCount(semOfferings.size()).build())
                .averageMapping(ProgrammeAttainmentResultDto.MappingBreakdown.builder().pos(poMappingBreakdown).psos(psoMappingBreakdown).build())
                .averageDirectAttainment(ProgrammeAttainmentResultDto.DirectAttainmentBreakdown.builder().pos(poDirectBreakdown).psos(psoDirectBreakdown).build())
                .averageIndirectAttainment(indirectMap)
                .overallAttainment(ProgrammeAttainmentResultDto.OverallAttainmentBreakdown.builder().pos(poOverallList).psos(psoOverallList).build())
                .courseMappingRows(courseMappingRows)
                .courseDirectAttainmentRows(courseDirectAttainmentRows)
                .studentSurveyRows(studentRows)
                .build();
    }

    public ProgrammeAttainmentDatasetDto getProgrammeAttainmentDataset(String masterProgrammeId, String programmeBatchId) {
        log.debug("[AttainmentCalculationService] getProgrammeAttainmentDataset called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        ProgrammeAttainmentResultDto res = calculateProgrammeAttainment(masterProgrammeId, programmeBatchId);

        MasterProgramme prog = masterProgrammeRepository.findById(masterProgrammeId).orElse(null);
        int duration = (prog != null && prog.getDurationYears() != null && prog.getDurationYears() > 0) ? prog.getDurationYears() : 4;
        int maxSem = duration * 2;
        if (res.getSummary() != null && res.getSummary().getSemesterCount() > maxSem) {
            maxSem = res.getSummary().getSemesterCount();
        }

        List<String> columns = new ArrayList<>();
        columns.add("Outcome");
        for (int s = 1; s <= maxSem; s++) {
            columns.add("Sem " + s);
        }
        columns.add("Average");

        List<Map<String, Object>> mapRows = new ArrayList<>();
        if (res.getAverageMapping() != null && res.getAverageMapping().getPos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : res.getAverageMapping().getPos()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("outcome", it.getPoCode());
                if (it.getSemesterValues() != null) {
                    for (ProgrammeAttainmentResultDto.SemesterValue sv : it.getSemesterValues()) {
                        r.put("sem" + sv.getSemester(), sv.getAverageMapping());
                    }
                }
                r.put("average", it.getOverallAverage());
                mapRows.add(r);
            }
        }
        if (res.getAverageMapping() != null && res.getAverageMapping().getPsos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : res.getAverageMapping().getPsos()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("outcome", it.getPsoCode());
                if (it.getSemesterValues() != null) {
                    for (ProgrammeAttainmentResultDto.SemesterValue sv : it.getSemesterValues()) {
                        r.put("sem" + sv.getSemester(), sv.getAverageMapping());
                    }
                }
                r.put("average", it.getOverallAverage());
                mapRows.add(r);
            }
        }

        List<Map<String, Object>> dirRows = new ArrayList<>();
        if (res.getAverageDirectAttainment() != null && res.getAverageDirectAttainment().getPos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : res.getAverageDirectAttainment().getPos()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("outcome", it.getPoCode());
                if (it.getSemesterValues() != null) {
                    for (ProgrammeAttainmentResultDto.SemesterValue sv : it.getSemesterValues()) {
                        r.put("sem" + sv.getSemester(), sv.getAverageAttainment());
                    }
                }
                r.put("average", it.getOverallAverage());
                dirRows.add(r);
            }
        }
        if (res.getAverageDirectAttainment() != null && res.getAverageDirectAttainment().getPsos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : res.getAverageDirectAttainment().getPsos()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("outcome", it.getPsoCode());
                if (it.getSemesterValues() != null) {
                    for (ProgrammeAttainmentResultDto.SemesterValue sv : it.getSemesterValues()) {
                        r.put("sem" + sv.getSemester(), sv.getAverageAttainment());
                    }
                }
                r.put("average", it.getOverallAverage());
                dirRows.add(r);
            }
        }

        Map<String, BigDecimal> overallMap = new LinkedHashMap<>();
        if (res.getOverallAttainment() != null && res.getOverallAttainment().getPos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPos()) {
                overallMap.put(it.getOutcomeCode(), it.getOverallAttainment());
            }
        }
        if (res.getOverallAttainment() != null && res.getOverallAttainment().getPsos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPsos()) {
                overallMap.put(it.getOutcomeCode(), it.getOverallAttainment());
            }
        }

        return ProgrammeAttainmentDatasetDto.builder()
                .masterProgrammeId(masterProgrammeId)
                .programmeBatchId(programmeBatchId)
                .averageMapping(ProgrammeAttainmentDatasetDto.TableData.builder().columns(columns).rows(mapRows).build())
                .averageDirectAttainment(ProgrammeAttainmentDatasetDto.TableData.builder().columns(columns).rows(dirRows).build())
                .averageIndirectAttainment(res.getAverageIndirectAttainment())
                .overallAttainment(overallMap)
                .build();
    }


    private <T> T getCoMapValue(Map<String, T> map, String key, int index, T fallback) {
        if (map == null || map.isEmpty()) return fallback;
        if (map.containsKey(key)) return map.get(key);
        for (Map.Entry<String, T> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        List<T> values = new ArrayList<>(map.values());
        if (index < values.size()) return values.get(index);
        return fallback;
    }

    private List<CourseOutcome> getConfiguredCourseOutcomes(String offeringId, String masterCourseId) {
        List<CourseOutcome> cos = (offeringId != null && !offeringId.isBlank())
                ? courseOutcomeRepository.findByProgrammeBatchCourseId(offeringId)
                : Collections.emptyList();
        if ((cos == null || cos.isEmpty()) && masterCourseId != null && !masterCourseId.isBlank()) {
            List<ProgrammeBatchCourse> relatedOfferings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
            if (relatedOfferings != null && !relatedOfferings.isEmpty()) {
                List<String> offIds = relatedOfferings.stream().map(ProgrammeBatchCourse::getId).toList();
                cos = courseOutcomeRepository.findByProgrammeBatchCourseIdIn(offIds);
            }
        }
        return cos != null ? cos : Collections.emptyList();
    }
}
