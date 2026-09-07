package com.dypiu.nba.reports.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.reports.model.ReportType;
import com.dypiu.nba.reports.model.snapshot.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.AcademicService;
import com.dypiu.nba.service.AtrService;
import com.dypiu.nba.service.AttainmentReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportSnapshotBuilder {

    private final AttainmentReportService attainmentReportService;
    private final AtrService atrService;
    private final AcademicService academicService;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;

    public ProgrammeAttainmentSnapshot buildProgrammeAttainmentSnapshot(
            String masterProgrammeId,
            String programmeBatchId,
            String generatedBy,
            String institutionId) {

        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new IllegalArgumentException("Programme batch not found: " + programmeBatchId));

        String progId = (masterProgrammeId != null && !masterProgrammeId.isBlank())
                ? masterProgrammeId
                : batch.getMasterProgrammeId();

        MasterProgramme programme = (progId != null)
                ? masterProgrammeRepository.findById(progId).orElse(null)
                : null;

        String schoolName = resolveSchoolNameForProgramme(programme);

        ProgrammeBatchAttainmentReportDto report = attainmentReportService.getOrCreateProgrammeAttainmentReport(progId, programmeBatchId);

        // 1. Extract dynamic PO & PSO Codes
        Set<String> poCodeSet = new LinkedHashSet<>();
        Set<String> psoCodeSet = new LinkedHashSet<>();

        if (report.getReport1AverageMappingPO() != null) {
            report.getReport1AverageMappingPO().forEach(r -> {
                if (r.getPoCode() != null) poCodeSet.add(r.getPoCode().toUpperCase());
            });
        }
        if (report.getReport1AverageMappingPSO() != null) {
            report.getReport1AverageMappingPSO().forEach(r -> {
                if (r.getPsoCode() != null) psoCodeSet.add(r.getPsoCode().toUpperCase());
            });
        }
        if (report.getCourseMappingRows() != null) {
            report.getCourseMappingRows().forEach(r -> {
                if (r.getPoValues() != null) poCodeSet.addAll(r.getPoValues().keySet());
                if (r.getPsoValues() != null) psoCodeSet.addAll(r.getPsoValues().keySet());
            });
        }

        List<String> sortedPoCodes = poCodeSet.stream()
                .sorted((a, b) -> extractNumber(a) - extractNumber(b))
                .collect(Collectors.toList());

        List<String> sortedPsoCodes = psoCodeSet.stream()
                .sorted((a, b) -> extractNumber(a) - extractNumber(b))
                .collect(Collectors.toList());

        // 2. Section 1: Average Mapping
        Map<String, BigDecimal> avgMappingStrength = new LinkedHashMap<>();
        BigDecimal mappingSum = BigDecimal.ZERO;
        int mappingCount = 0;

        if (report.getReport1AverageMappingPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PoRow r : report.getReport1AverageMappingPO()) {
                if (r.getPoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPoCode().toUpperCase(), r.getProgrammeAverageMapping());
                    mappingSum = mappingSum.add(r.getProgrammeAverageMapping());
                    mappingCount++;
                }
            }
        }
        if (report.getReport1AverageMappingPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report1PsoRow r : report.getReport1AverageMappingPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeAverageMapping() != null) {
                    avgMappingStrength.put(r.getPsoCode().toUpperCase(), r.getProgrammeAverageMapping());
                    mappingSum = mappingSum.add(r.getProgrammeAverageMapping());
                    mappingCount++;
                }
            }
        }

        List<ProgrammeAttainmentSnapshot.CourseMappingRow> mappingCourses = new ArrayList<>();
        if (report.getCourseMappingRows() != null) {
            for (ProgrammeBatchAttainmentReportDto.CourseContributionRow r : report.getCourseMappingRows()) {
                mappingCourses.add(ProgrammeAttainmentSnapshot.CourseMappingRow.builder()
                        .programmeBatchCourseId(r.getProgrammeBatchCourseId())
                        .courseCode(r.getCourseCode())
                        .courseName(r.getCourseName())
                        .semester(r.getSemester())
                        .poValues(r.getPoValues() != null ? new LinkedHashMap<>(r.getPoValues()) : new LinkedHashMap<>())
                        .psoValues(r.getPsoValues() != null ? new LinkedHashMap<>(r.getPsoValues()) : new LinkedHashMap<>())
                        .build());
            }
        }

        ProgrammeAttainmentSnapshot.AverageMappingSection section1 = ProgrammeAttainmentSnapshot.AverageMappingSection.builder()
                .courses(mappingCourses)
                .averageMappingStrength(avgMappingStrength)
                .overallAverageMappingStrength(mappingCount > 0 ? mappingSum.divide(BigDecimal.valueOf(mappingCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build();

        // 3. Section 2: Average Direct Attainment
        Map<String, BigDecimal> avgDirectAttainment = new LinkedHashMap<>();
        BigDecimal directSum = BigDecimal.ZERO;
        int directCount = 0;

        if (report.getReport2DirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PoRow r : report.getReport2DirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                    directSum = directSum.add(r.getProgrammeDirectAttainment());
                    directCount++;
                }
            }
        }
        if (report.getReport2DirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report2PsoRow r : report.getReport2DirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getProgrammeDirectAttainment() != null) {
                    avgDirectAttainment.put(r.getPsoCode().toUpperCase(), r.getProgrammeDirectAttainment());
                    directSum = directSum.add(r.getProgrammeDirectAttainment());
                    directCount++;
                }
            }
        }

        List<ProgrammeAttainmentSnapshot.CourseDirectRow> directCourses = new ArrayList<>();
        if (report.getCourseDirectAttainmentRows() != null) {
            for (ProgrammeBatchAttainmentReportDto.CourseContributionRow r : report.getCourseDirectAttainmentRows()) {
                directCourses.add(ProgrammeAttainmentSnapshot.CourseDirectRow.builder()
                        .programmeBatchCourseId(r.getProgrammeBatchCourseId())
                        .courseCode(r.getCourseCode())
                        .courseName(r.getCourseName())
                        .semester(r.getSemester())
                        .poValues(r.getPoValues() != null ? new LinkedHashMap<>(r.getPoValues()) : new LinkedHashMap<>())
                        .psoValues(r.getPsoValues() != null ? new LinkedHashMap<>(r.getPsoValues()) : new LinkedHashMap<>())
                        .build());
            }
        }

        ProgrammeAttainmentSnapshot.AverageDirectSection section2 = ProgrammeAttainmentSnapshot.AverageDirectSection.builder()
                .courses(directCourses)
                .averageDirectAttainment(avgDirectAttainment)
                .overallDirectAttainment(directCount > 0 ? directSum.divide(BigDecimal.valueOf(directCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build();

        // 4. Section 3: Average Indirect Attainment
        Map<String, BigDecimal> avgIndirectAttainment = new LinkedHashMap<>();
        BigDecimal indirectSum = BigDecimal.ZERO;
        int indirectCount = 0;

        if (report.getReport3IndirectAttainmentPO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PoRow r : report.getReport3IndirectAttainmentPO()) {
                if (r.getPoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                    indirectSum = indirectSum.add(r.getIndirectAttainmentLevel());
                    indirectCount++;
                }
            }
        }
        if (report.getReport3IndirectAttainmentPSO() != null) {
            for (ProgrammeBatchAttainmentReportDto.Report3PsoRow r : report.getReport3IndirectAttainmentPSO()) {
                if (r.getPsoCode() != null && r.getIndirectAttainmentLevel() != null) {
                    avgIndirectAttainment.put(r.getPsoCode().toUpperCase(), r.getIndirectAttainmentLevel());
                    indirectSum = indirectSum.add(r.getIndirectAttainmentLevel());
                    indirectCount++;
                }
            }
        }

        List<ProgrammeAttainmentSnapshot.StudentSurveyRow> studentResponses = new ArrayList<>();
        if (report.getStudentSurveyRows() != null) {
            for (ProgrammeBatchAttainmentReportDto.StudentSurveyResponseRow r : report.getStudentSurveyRows()) {
                Map<String, BigDecimal> poMap = new LinkedHashMap<>();
                if (r.getPoRatings() != null) {
                    r.getPoRatings().forEach((k, v) -> {
                        if (v != null && !v.isBlank()) {
                            try { poMap.put(k, new BigDecimal(v.trim())); } catch (Exception ignored) {}
                        }
                    });
                }
                Map<String, BigDecimal> psoMap = new LinkedHashMap<>();
                if (r.getPsoRatings() != null) {
                    r.getPsoRatings().forEach((k, v) -> {
                        if (v != null && !v.isBlank()) {
                            try { psoMap.put(k, new BigDecimal(v.trim())); } catch (Exception ignored) {}
                        }
                    });
                }

                studentResponses.add(ProgrammeAttainmentSnapshot.StudentSurveyRow.builder()
                        .srNo(r.getSrNo())
                        .prn(r.getPrn())
                        .studentName(r.getStudentName())
                        .poRatings(poMap)
                        .psoRatings(psoMap)
                        .build());
            }
        }

        ProgrammeAttainmentSnapshot.AverageIndirectSection section3 = ProgrammeAttainmentSnapshot.AverageIndirectSection.builder()
                .surveyType("Graduate Exit Survey")
                .totalStudents(studentResponses.size())
                .studentResponses(studentResponses)
                .averageIndirectAttainment(avgIndirectAttainment)
                .overallIndirectAttainment(indirectCount > 0 ? indirectSum.divide(BigDecimal.valueOf(indirectCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build();

        // 5. Section 4: Overall Attainment
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

        ProgrammeAttainmentSnapshot.OverallAttainmentSection section4 = ProgrammeAttainmentSnapshot.OverallAttainmentSection.builder()
                .directWeightPercentage(new BigDecimal("80.00"))
                .indirectWeightPercentage(new BigDecimal("20.00"))
                .averageMappingStrength(avgMappingStrength)
                .averageDirectAttainment(avgDirectAttainment)
                .averageIndirectAttainment(avgIndirectAttainment)
                .finalAttainments(finalAttainments)
                .overallProgrammeAttainment(report.getOverallProgrammeAttainment() != null ? report.getOverallProgrammeAttainment() : BigDecimal.ZERO)
                .build();

        String batchYears = (batch.getStartYear() != null && batch.getEndYear() != null)
                ? (batch.getStartYear() + "-" + batch.getEndYear())
                : batch.getName();

        return ProgrammeAttainmentSnapshot.builder()
                .reportType(ReportType.PROGRAMME_ATTAINMENT)
                .institutionId(institutionId != null ? institutionId : "DYPIU")
                .institutionName("D. Y. PATIL INTERNATIONAL UNIVERSITY, PUNE")
                .schoolName(schoolName)
                .academicYear(batchYears)
                .generatedBy(generatedBy != null ? generatedBy : "System")
                .generatedAt(ZonedDateTime.now())
                .masterProgrammeId(progId)
                .masterProgrammeCode(programme != null ? programme.getCode() : "")
                .masterProgrammeName(programme != null ? programme.getName() : batch.getProgrammeName())
                .programmeBatchId(programmeBatchId)
                .programmeBatchName(batch.getName())
                .academicBatchYears(batchYears)
                .poCodes(sortedPoCodes)
                .psoCodes(sortedPsoCodes)
                .section1AverageMapping(section1)
                .section2AverageDirect(section2)
                .section3AverageIndirect(section3)
                .section4OverallAttainment(section4)
                .build();
    }

    public CourseAttainmentSnapshot buildCourseAttainmentSnapshot(
            String programmeBatchCourseId,
            String generatedBy,
            String institutionId) {

        ProgrammeBatchCourse pbc = programmeBatchCourseRepository.findById(programmeBatchCourseId)
                .orElseThrow(() -> new IllegalArgumentException("Course offering not found: " + programmeBatchCourseId));

        ProgrammeBatch batch = programmeBatchRepository.findById(pbc.getProgrammeBatchId()).orElse(null);

        String schoolName = resolveSchoolNameForOffering(pbc);

        CourseAttainmentReportDto dto = attainmentReportService.getOrCreateCourseAttainmentReport(programmeBatchCourseId);

        List<CourseAttainmentSnapshot.CoMappingRow> t1 = new ArrayList<>();
        if (dto.getTable1Mapping() != null) {
            for (CourseAttainmentReportDto.Table1Row r : dto.getTable1Mapping()) {
                t1.add(CourseAttainmentSnapshot.CoMappingRow.builder()
                        .coCode(r.getCoCode())
                        .poMappings(r.getPoMappings() != null ? new LinkedHashMap<>(r.getPoMappings()) : new LinkedHashMap<>())
                        .psoMappings(r.getPsoMappings() != null ? new LinkedHashMap<>(r.getPsoMappings()) : new LinkedHashMap<>())
                        .build());
            }
        }

        List<CourseAttainmentSnapshot.OutcomeContributionRow> t2Po = new ArrayList<>();
        if (dto.getTable2DirectPO() != null) {
            for (CourseAttainmentReportDto.Table2PoRow r : dto.getTable2DirectPO()) {
                t2Po.add(CourseAttainmentSnapshot.OutcomeContributionRow.builder()
                        .outcomeCode(r.getPoCode())
                        .averageMapping(r.getAverageMapping())
                        .directContribution(r.getDirectContribution())
                        .build());
            }
        }

        List<CourseAttainmentSnapshot.OutcomeContributionRow> t2Pso = new ArrayList<>();
        if (dto.getTable2DirectPSO() != null) {
            for (CourseAttainmentReportDto.Table2PsoRow r : dto.getTable2DirectPSO()) {
                t2Pso.add(CourseAttainmentSnapshot.OutcomeContributionRow.builder()
                        .outcomeCode(r.getPsoCode())
                        .averageMapping(r.getAverageMapping())
                        .directContribution(r.getDirectContribution())
                        .build());
            }
        }

        List<CourseAttainmentSnapshot.CoAttainmentRow> t3 = new ArrayList<>();
        if (dto.getTable3CoAttainments() != null) {
            for (CourseAttainmentReportDto.Table3Row r : dto.getTable3CoAttainments()) {
                t3.add(CourseAttainmentSnapshot.CoAttainmentRow.builder()
                        .coCode(r.getCoCode())
                        .statement(r.getStatement())
                        .targetLevel(r.getTargetLevel())
                        .directPercentage(r.getDirectPercentage())
                        .directLevel(r.getDirectLevel())
                        .indirectPercentage(r.getIndirectPercentage())
                        .indirectScore(r.getIndirectScore())
                        .indirectLevel(r.getIndirectLevel())
                        .finalAttainment(r.getFinalAttainment())
                        .targetMet(r.getTargetMet())
                        .build());
            }
        }

        Set<String> poSet = new LinkedHashSet<>();
        Set<String> psoSet = new LinkedHashSet<>();
        t1.forEach(r -> {
            poSet.addAll(r.getPoMappings().keySet());
            psoSet.addAll(r.getPsoMappings().keySet());
        });

        List<String> sortedPo = poSet.stream().sorted((a, b) -> extractNumber(a) - extractNumber(b)).toList();
        List<String> sortedPso = psoSet.stream().sorted((a, b) -> extractNumber(a) - extractNumber(b)).toList();

        String batchYears = (batch != null && batch.getStartYear() != null && batch.getEndYear() != null)
                ? batch.getStartYear() + "-" + batch.getEndYear()
                : (batch != null ? batch.getName() : "");

        return CourseAttainmentSnapshot.builder()
                .reportType(ReportType.COURSE_ATTAINMENT)
                .institutionId(institutionId != null ? institutionId : "DYPIU")
                .institutionName("D. Y. PATIL INTERNATIONAL UNIVERSITY, PUNE")
                .schoolName(schoolName)
                .academicYear(batchYears)
                .generatedBy(generatedBy != null ? generatedBy : "Course Coordinator")
                .generatedAt(ZonedDateTime.now())
                .programmeBatchCourseId(programmeBatchCourseId)
                .masterCourseId(pbc.getMasterCourseId())
                .courseCode(pbc.getEffectiveCourseCode() != null ? pbc.getEffectiveCourseCode() : "")
                .courseName(pbc.getEffectiveCourseName() != null ? pbc.getEffectiveCourseName() : "")
                .semester(pbc.getSemester())
                .programmeBatchId(pbc.getProgrammeBatchId())
                .batchName(batch != null ? batch.getName() : "")
                .overallCoAttainment(dto.getOverallCoAttainment())
                .directAttainment(dto.getDirectAttainment())
                .indirectAttainment(dto.getIndirectAttainment())
                .poCodes(sortedPo)
                .psoCodes(sortedPso)
                .table1Mapping(t1)
                .table2DirectPO(t2Po)
                .table2DirectPSO(t2Pso)
                .table3CoAttainments(t3)
                .build();
    }

    public ProgrammeAtrSnapshot buildProgrammeAtrSnapshot(
            String masterProgrammeId,
            String programmeBatchId,
            String generatedBy,
            String institutionId) {

        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new IllegalArgumentException("Programme batch not found: " + programmeBatchId));

        String progId = (masterProgrammeId != null && !masterProgrammeId.isBlank()) ? masterProgrammeId : batch.getMasterProgrammeId();
        MasterProgramme programme = (progId != null) ? masterProgrammeRepository.findById(progId).orElse(null) : null;
        String schoolName = resolveSchoolNameForProgramme(programme);

        ProgrammeAtrReportDto dto = atrService.getProgrammeAtrReport(progId, programmeBatchId);

        List<ProgrammeAtrSnapshot.AtrOutcomeRow> pos = new ArrayList<>();
        if (dto.getPoOutcomes() != null) {
            for (ProgrammeAtrReportDto.OutcomeRow r : dto.getPoOutcomes()) {
                pos.add(ProgrammeAtrSnapshot.AtrOutcomeRow.builder()
                        .outcomeCode(r.getOutcomeCode())
                        .outcomeStatement(r.getOutcomeStatement())
                        .targetLevel(r.getTargetLevel())
                        .attainmentLevel(r.getAttainmentLevel())
                        .achievementPercentage(r.getAchievementPercentage())
                        .actions(r.getActions() != null ? new ArrayList<>(r.getActions()) : new ArrayList<>())
                        .build());
            }
        }

        List<ProgrammeAtrSnapshot.AtrOutcomeRow> psos = new ArrayList<>();
        if (dto.getPsoOutcomes() != null) {
            for (ProgrammeAtrReportDto.OutcomeRow r : dto.getPsoOutcomes()) {
                psos.add(ProgrammeAtrSnapshot.AtrOutcomeRow.builder()
                        .outcomeCode(r.getOutcomeCode())
                        .outcomeStatement(r.getOutcomeStatement())
                        .targetLevel(r.getTargetLevel())
                        .attainmentLevel(r.getAttainmentLevel())
                        .achievementPercentage(r.getAchievementPercentage())
                        .actions(r.getActions() != null ? new ArrayList<>(r.getActions()) : new ArrayList<>())
                        .build());
            }
        }

        String batchYears = (batch.getStartYear() != null && batch.getEndYear() != null)
                ? batch.getStartYear() + "-" + batch.getEndYear()
                : batch.getName();

        return ProgrammeAtrSnapshot.builder()
                .reportType(ReportType.PROGRAMME_ATR)
                .institutionId(institutionId != null ? institutionId : "DYPIU")
                .institutionName("D. Y. PATIL INTERNATIONAL UNIVERSITY, PUNE")
                .schoolName(schoolName)
                .academicYear(batchYears)
                .generatedBy(generatedBy != null ? generatedBy : "Programme Coordinator")
                .generatedAt(ZonedDateTime.now())
                .programmeAtrId(dto.getProgrammeAtrId())
                .masterProgrammeId(progId)
                .masterProgrammeCode(dto.getProgramme() != null ? dto.getProgramme().getCode() : (programme != null ? programme.getCode() : ""))
                .masterProgrammeName(dto.getProgramme() != null ? dto.getProgramme().getName() : (programme != null ? programme.getName() : ""))
                .programmeBatchId(programmeBatchId)
                .batchName(dto.getBatch() != null ? dto.getBatch().getName() : batch.getName())
                .startYear(dto.getBatch() != null ? dto.getBatch().getStartYear() : "")
                .endYear(dto.getBatch() != null ? dto.getBatch().getEndYear() : "")
                .status(dto.getStatus())
                .poOutcomes(pos)
                .psoOutcomes(psos)
                .build();
    }

    public CourseAtrSnapshot buildCourseAtrSnapshot(
            String programmeBatchCourseId,
            String generatedBy,
            String institutionId) {

        ProgrammeBatchCourse pbc = programmeBatchCourseRepository.findById(programmeBatchCourseId)
                .orElseThrow(() -> new IllegalArgumentException("Course offering not found: " + programmeBatchCourseId));

        ProgrammeBatch batch = programmeBatchRepository.findById(pbc.getProgrammeBatchId()).orElse(null);
        String schoolName = resolveSchoolNameForOffering(pbc);

        CourseAtrReportDto dto = atrService.getCourseAtrReport(programmeBatchCourseId);

        List<CourseAtrSnapshot.AtrOutcomeRow> outcomes = new ArrayList<>();
        if (dto.getOutcomes() != null) {
            for (CourseAtrReportDto.OutcomeRow r : dto.getOutcomes()) {
                outcomes.add(CourseAtrSnapshot.AtrOutcomeRow.builder()
                        .outcomeCode(r.getOutcomeCode())
                        .outcomeStatement(r.getOutcomeStatement())
                        .targetLevel(r.getTargetLevel())
                        .attainmentLevel(r.getAttainmentLevel())
                        .achievementPercentage(r.getAchievementPercentage())
                        .actions(r.getActions() != null ? new ArrayList<>(r.getActions()) : new ArrayList<>())
                        .build());
            }
        }

        String batchYears = (batch != null && batch.getStartYear() != null && batch.getEndYear() != null)
                ? batch.getStartYear() + "-" + batch.getEndYear()
                : (batch != null ? batch.getName() : "");

        return CourseAtrSnapshot.builder()
                .reportType(ReportType.COURSE_ATR)
                .institutionId(institutionId != null ? institutionId : "DYPIU")
                .institutionName("D. Y. PATIL INTERNATIONAL UNIVERSITY, PUNE")
                .schoolName(schoolName)
                .academicYear(batchYears)
                .generatedBy(generatedBy != null ? generatedBy : "Course Coordinator")
                .generatedAt(ZonedDateTime.now())
                .courseAtrId(dto.getCourseAtrId())
                .programmeBatchCourseId(programmeBatchCourseId)
                .masterCourseId(dto.getCourseOffering() != null ? dto.getCourseOffering().getMasterCourseId() : pbc.getMasterCourseId())
                .courseCode(dto.getCourse() != null ? dto.getCourse().getCode() : (pbc.getCourseCodeOverride() != null ? pbc.getCourseCodeOverride() : ""))
                .courseName(dto.getCourse() != null ? dto.getCourse().getName() : (pbc.getCourseNameOverride() != null ? pbc.getCourseNameOverride() : ""))
                .semester(dto.getCourseOffering() != null ? dto.getCourseOffering().getSemester() : pbc.getSemester())
                .programmeBatchId(dto.getCourseOffering() != null ? dto.getCourseOffering().getProgrammeBatchId() : pbc.getProgrammeBatchId())
                .batchName(dto.getBatch() != null ? dto.getBatch().getName() : (batch != null ? batch.getName() : ""))
                .status(dto.getStatus())
                .outcomes(outcomes)
                .build();
    }

    private String resolveSchoolNameForProgramme(MasterProgramme programme) {
        if (programme == null || programme.getDepartmentId() == null) {
            return "School of Engineering and Technology";
        }
        try {
            Department dept = departmentRepository.findById(programme.getDepartmentId()).orElse(null);
            if (dept != null && dept.getSchoolId() != null) {
                School school = schoolRepository.findById(dept.getSchoolId()).orElse(null);
                if (school != null && school.getName() != null && !school.getName().isBlank()) {
                    return school.getName();
                }
            }
            if (dept != null && dept.getName() != null) {
                return dept.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve school name for programme: {}", programme.getId(), e);
        }
        return (programme.getDepartmentName() != null && !programme.getDepartmentName().isBlank())
                ? programme.getDepartmentName()
                : "School of Engineering and Technology";
    }

    private String resolveSchoolNameForOffering(ProgrammeBatchCourse pbc) {
        if (pbc == null) return "School of Engineering and Technology";
        MasterProgramme programme = null;
        if (pbc.getProgrammeBatchId() != null) {
            ProgrammeBatch batch = programmeBatchRepository.findById(pbc.getProgrammeBatchId()).orElse(null);
            if (batch != null && batch.getMasterProgrammeId() != null) {
                programme = masterProgrammeRepository.findById(batch.getMasterProgrammeId()).orElse(null);
            }
        }
        return resolveSchoolNameForProgramme(programme);
    }

    private int extractNumber(String code) {
        if (code == null) return 999;
        String digits = code.replaceAll("\\D+", "");
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 999;
        }
    }
}
