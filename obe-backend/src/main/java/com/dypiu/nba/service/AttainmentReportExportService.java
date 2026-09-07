package com.dypiu.nba.service;

import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttainmentReportExportService {

    private final MasterProgrammeRepository masterProgrammeRepository;
    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final StudentCoMarkRepository studentCoMarkRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final AttainmentCalculationService calculationService;
    private final OutcomeService outcomeService;

    private static final String TEMPLATE_PATH = "templates/Template-CO-PO-PSO-Attainment-TH-v2-Sample.xlsx";
    private static final String EXTERNAL_TEMPLATE_PATH = "/Users/rajshaikh/Desktop/Template-CO-PO-PSO-Attainment-TH-v2-Sample.xlsx";

    /**
     * Generates a fully populated Excel spreadsheet matching the DYPIU NBA Attainment Template.
     */
    @Transactional(readOnly = true)
    public byte[] generateAttainmentExcel(String masterCourseId, String programmeBatchId) {
        System.out.println("[AttainmentReportExportService] generateAttainmentExcel called | masterCourseId: " + masterCourseId + " | programmeBatchId: " + programmeBatchId);
        log.info("[AttainmentReportExportService] Generating Attainment Excel for masterCourseId: {}, programmeBatchId: {}", masterCourseId, programmeBatchId);

        ProgrammeBatchCourse course = programmeBatchCourseRepository.findById(masterCourseId).orElse(null);
        ProgrammeBatch batch = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            batch = programmeBatchRepository.findById(programmeBatchId).orElse(null);
        }
        if (batch == null && course != null && course.getProgrammeBatchId() != null) {
            batch = programmeBatchRepository.findById(course.getProgrammeBatchId()).orElse(null);
        }

        if (course == null) {
            course = ProgrammeBatchCourse.builder()
                    .id(masterCourseId)
                    .code("COURSE-310")
                    .name("Computer Networks and Security")
                    .build();
        }

        String masterProgrammeId = (batch != null) ? batch.getMasterProgrammeId() : null;
        MasterProgramme programme = (masterProgrammeId != null)
                ? masterProgrammeRepository.findById(masterProgrammeId).orElse(null)
                : null;
        Department department = (programme != null && programme.getDepartmentId() != null)
                ? departmentRepository.findById(programme.getDepartmentId()).orElse(null)
                : null;
        School school = (department != null && department.getSchoolId() != null)
                ? schoolRepository.findById(department.getSchoolId()).orElse(null)
                : null;

        String offeringId = course.getId();

        // Fetch CO calculations and mappings
        Map<String, Object> coCalcData = calculationService.calculateCourseCoAttainment(offeringId);
        CourseMappingMatrixDto mappingsDto = outcomeService.getCourseMappings(offeringId);
        List<CourseOutcome> cos = outcomeService.getCOsByCourse(offeringId);
        List<ProgrammeOutcome> pos = (programme != null) ? outcomeService.getPOsByProgramme(programme.getId()) : Collections.emptyList();
        List<ProgrammeSpecificOutcome> psos = (programme != null) ? outcomeService.getPSOsByProgramme(programme.getId()) : Collections.emptyList();
        List<StudentCoMark> studentMarks = studentCoMarkRepository.findByProgrammeBatchCourseId(offeringId);

        XSSFWorkbook workbook = loadTemplateWorkbook();

        try {
            populateAttainmentMainSheet(workbook, course, programme, department, school, batch, cos, pos, psos, mappingsDto, coCalcData);
            populateExaminationSheet(workbook, course, batch, cos, studentMarks, coCalcData);
            populateCourseEndSurveySheet(workbook, course, batch, cos, coCalcData);
            populateCoWiseEvaluationSheet(workbook, course, batch, cos, studentMarks);

            // Re-evaluate formula cells
            try {
                XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            } catch (Exception fe) {
                log.warn("Formula evaluation warning in Excel generation: {}", fe.getMessage());
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Excel attainment workbook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Attainment Excel sheet: " + e.getMessage(), e);
        }
    }

    private XSSFWorkbook loadTemplateWorkbook() {
        // 1. Try classpath resource
        try {
            ClassPathResource cpr = new ClassPathResource(TEMPLATE_PATH);
            if (cpr.exists()) {
                try (InputStream is = cpr.getInputStream()) {
                    return new XSSFWorkbook(is);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load template from classpath: {}", e.getMessage());
        }

        // 2. Try external desktop path
        try {
            File extFile = new File(EXTERNAL_TEMPLATE_PATH);
            if (extFile.exists()) {
                try (InputStream is = new FileInputStream(extFile)) {
                    return new XSSFWorkbook(is);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load template from external path: {}", e.getMessage());
        }

        // 3. Fallback: create a fresh workbook
        log.info("Creating blank fallback workbook for Attainment template");
        XSSFWorkbook wb = new XSSFWorkbook();
        wb.createSheet("Attainment-main");
        wb.createSheet("PO mapping");
        wb.createSheet("PSO mapping");
        wb.createSheet("Examination");
        wb.createSheet("Course End Survey");
        wb.createSheet("Concurrent Assessment");
        wb.createSheet("End Sem");
        wb.createSheet("CO-wise evaluation");
        return wb;
    }

    private void populateAttainmentMainSheet(
            XSSFWorkbook wb,
            ProgrammeBatchCourse course,
            MasterProgramme programme,
            Department department,
            School school,
            ProgrammeBatch batch,
            List<CourseOutcome> cos,
            List<ProgrammeOutcome> pos,
            List<ProgrammeSpecificOutcome> psos,
            CourseMappingMatrixDto mappingsDto,
            Map<String, Object> coCalcData) {

        XSSFSheet sheet = wb.getSheet("Attainment-main");
        if (sheet == null) sheet = wb.createSheet("Attainment-main");

        String schoolName = (school != null && school.getName() != null) ? school.getName() : "School of Science and Technology";
        String courseName = course.getName() != null ? course.getName() : "Course Name";
        String courseCode = course.getCode() != null ? course.getCode() : "COURSE-CODE";
        String semester = "Semester 5";
        String batchYear = (batch != null && batch.getName() != null) ? batch.getName() : "Batch 2025-29";
        String facultyName = "Course Coordinator";


        // Set Headers
        setCellValue(sheet, 1, 2, schoolName);         // Row 2 (0-indexed: 1), Col C (0-indexed: 2)
        setCellValue(sheet, 5, 2, courseName);         // Row 6, Col C
        setCellValue(sheet, 6, 2, batchYear);          // Row 7, Col C
        setCellValue(sheet, 7, 2, semester);           // Row 8, Col C
        setCellValue(sheet, 8, 2, courseCode);         // Row 9, Col C
        setCellValue(sheet, 9, 2, facultyName);        // Row 10, Col C

        // Course Outcomes Table (Rows 14..19, 0-indexed: 13..18)
        for (int i = 0; i < cos.size(); i++) {
            CourseOutcome co = cos.get(i);
            int rIdx = 13 + i;
            setCellValue(sheet, rIdx, 0, i + 1);
            setCellValue(sheet, rIdx, 1, co.getCode());
            setCellValue(sheet, rIdx, 2, co.getStatement());
        }

        // Table 1: Mapping of CO to PO/PSO (Rows 23..28, 0-indexed: 22..27)
        // Columns: A=Sr, B=Code, C=PO1, D=PO2 ... N=PO12, O=PSO1, P=PSO2, Q=PSO3
        Map<String, Map<String, Integer>> coPoMap = new HashMap<>();
        Map<String, Map<String, Integer>> coPsoMap = new HashMap<>();

        if (mappingsDto != null) {
            if (mappingsDto.getPoMappings() != null) {
                for (CoPoMapping m : mappingsDto.getPoMappings()) {
                    CourseOutcome co = cos.stream().filter(c -> c.getId().equals(m.getCourseOutcomeId())).findFirst().orElse(null);
                    String coCode = co != null ? co.getCode() : null;
                    if (coCode != null && m.getPoCode() != null) {
                        coPoMap.computeIfAbsent(coCode, k -> new HashMap<>()).put(m.getPoCode(), m.getMappingLevel());
                    }
                }
            }
            if (mappingsDto.getPsoMappings() != null) {
                for (CoPsoMapping m : mappingsDto.getPsoMappings()) {
                    CourseOutcome co = cos.stream().filter(c -> c.getId().equals(m.getCourseOutcomeId())).findFirst().orElse(null);
                    String coCode = co != null ? co.getCode() : null;
                    if (coCode != null && m.getPsoCode() != null) {
                        coPsoMap.computeIfAbsent(coCode, k -> new HashMap<>()).put(m.getPsoCode(), m.getMappingLevel());
                    }
                }
            }
        }

        for (int i = 0; i < cos.size(); i++) {
            CourseOutcome co = cos.get(i);
            String coCode = co.getCode();
            int rIdx = 22 + i;

            setCellValue(sheet, rIdx, 0, i + 1);
            setCellValue(sheet, rIdx, 1, coCode);

            // PO1 to PO12 (Col C to N: 2 to 13)
            for (int p = 1; p <= 12; p++) {
                String poCode = "PO" + p;
                Integer lvl = (coPoMap.containsKey(coCode)) ? coPoMap.get(coCode).get(poCode) : null;
                if (lvl != null && lvl > 0) {
                    setCellValue(sheet, rIdx, 1 + p, lvl);
                } else {
                    setCellValue(sheet, rIdx, 1 + p, "-");
                }
            }

            // PSO1 to PSO3 (Col O to Q: 14 to 16)
            for (int ps = 1; ps <= 3; ps++) {
                String psoCode = "PSO" + ps;
                Integer lvl = (coPsoMap.containsKey(coCode)) ? coPsoMap.get(coCode).get(psoCode) : null;
                if (lvl != null && lvl > 0) {
                    setCellValue(sheet, rIdx, 13 + ps, lvl);
                } else {
                    setCellValue(sheet, rIdx, 13 + ps, "-");
                }
            }
        }

        // Reference Work (Rows 41..48, 0-indexed: 40..47)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) coCalcData.get("coAttainments");
        BigDecimal overallCoAttainment = (BigDecimal) coCalcData.get("overallCoAttainment");
        if (overallCoAttainment == null) overallCoAttainment = BigDecimal.ZERO;

        if (coAttainments != null) {
            for (int i = 0; i < coAttainments.size() && i < 6; i++) {
                Map<String, Object> coMap = coAttainments.get(i);
                int cIdx = 3 + i; // Col D to I (3 to 8)

                BigDecimal directPct = (BigDecimal) coMap.get("directPct");
                Integer directLvl = (Integer) coMap.get("directLevel");
                BigDecimal indirectPct = (BigDecimal) coMap.get("indirectPct");
                Integer indirectLvl = (Integer) coMap.get("indirectLevel");
                BigDecimal combined = (BigDecimal) coMap.get("combinedAttainment");

                if (directPct != null) setCellValue(sheet, 40, cIdx, directPct.doubleValue());
                if (directLvl != null) setCellValue(sheet, 41, cIdx, directLvl);
                if (indirectPct != null) setCellValue(sheet, 42, cIdx, indirectPct.doubleValue());
                if (indirectLvl != null) setCellValue(sheet, 43, cIdx, indirectLvl);
                if (combined != null) setCellValue(sheet, 45, cIdx, combined.doubleValue());
            }
        }

        setCellValue(sheet, 31, 0, overallCoAttainment.doubleValue()); // Overall CO Attainment cell A32
        setCellValue(sheet, 47, 3, overallCoAttainment.doubleValue()); // Row 48 Col D
    }

    private void populateExaminationSheet(
            XSSFWorkbook wb,
            ProgrammeBatchCourse course,
            ProgrammeBatch batch,
            List<CourseOutcome> cos,
            List<StudentCoMark> studentMarks,
            Map<String, Object> coCalcData) {

        XSSFSheet sheet = wb.getSheet("Examination");
        if (sheet == null) return;

        // Group student marks by PRN
        Map<String, Map<String, BigDecimal>> studentMarkMap = new LinkedHashMap<>();
        Map<String, String> studentNameMap = new LinkedHashMap<>();

        if (studentMarks != null && !studentMarks.isEmpty()) {
            for (StudentCoMark sm : studentMarks) {
                String prn = sm.getPrn();
                if (prn == null) continue;
                studentNameMap.putIfAbsent(prn, sm.getStudentName() != null ? sm.getStudentName() : "Student " + prn);
                studentMarkMap.computeIfAbsent(prn, k -> new HashMap<>()).put(sm.getCoCode(), sm.getMarksObtained());
            }
        }

        int startRow = 22; // Row 23 (0-indexed: 22)
        int idx = 1;
        for (Map.Entry<String, String> entry : studentNameMap.entrySet()) {
            String prn = entry.getKey();
            String name = entry.getValue();
            Map<String, BigDecimal> coMarks = studentMarkMap.get(prn);

            int r = startRow + (idx - 1);
            setCellValue(sheet, r, 0, idx);
            setCellValue(sheet, r, 1, prn);
            setCellValue(sheet, r, 2, name);

            // CO1 to CO6 (Col F to K: 5 to 10)
            for (int c = 0; c < cos.size() && c < 6; c++) {
                String coCode = cos.get(c).getCode();
                BigDecimal mark = (coMarks != null && coMarks.containsKey(coCode)) ? coMarks.get(coCode) : null;
                if (mark != null) {
                    setCellValue(sheet, r, 5 + c, mark.doubleValue());
                }
            }
            idx++;
        }
    }

    private void populateCourseEndSurveySheet(
            XSSFWorkbook wb,
            ProgrammeBatchCourse course,
            ProgrammeBatch batch,
            List<CourseOutcome> cos,
            Map<String, Object> coCalcData) {

        XSSFSheet sheet = wb.getSheet("Course End Survey");
        if (sheet == null) return;

        SurveyAttainmentResultDto surveyDto = (SurveyAttainmentResultDto) coCalcData.get("surveyDetails");
        if (surveyDto == null) return;

        // Set counts and percentages for CO1..CO6
        if (surveyDto.getOverallIndirectPercentages() != null) {
            for (int i = 0; i < cos.size() && i < 6; i++) {
                String coCode = cos.get(i).getCode();
                BigDecimal pct = surveyDto.getOverallIndirectPercentages().get(coCode);
                if (pct != null) {
                    setCellValue(sheet, 14, 2 + i, pct.doubleValue()); // Row 15, Col C..H
                }
            }
        }
    }

    private void populateCoWiseEvaluationSheet(
            XSSFWorkbook wb,
            ProgrammeBatchCourse course,
            ProgrammeBatch batch,
            List<CourseOutcome> cos,
            List<StudentCoMark> studentMarks) {

        XSSFSheet sheet = wb.getSheet("CO-wise evaluation");
        if (sheet == null) return;

        // Populate student names & marks out of 20
        Map<String, Map<String, BigDecimal>> studentMarkMap = new LinkedHashMap<>();
        Map<String, String> studentNameMap = new LinkedHashMap<>();

        if (studentMarks != null && !studentMarks.isEmpty()) {
            for (StudentCoMark sm : studentMarks) {
                String prn = sm.getPrn();
                if (prn == null) continue;
                studentNameMap.putIfAbsent(prn, sm.getStudentName() != null ? sm.getStudentName() : "Student " + prn);
                studentMarkMap.computeIfAbsent(prn, k -> new HashMap<>()).put(sm.getCoCode(), sm.getMarksObtained());
            }
        }

        int startRow = 7; // Row 8 (0-indexed: 7)
        int idx = 1;
        for (Map.Entry<String, String> entry : studentNameMap.entrySet()) {
            String prn = entry.getKey();
            String name = entry.getValue();
            Map<String, BigDecimal> coMarks = studentMarkMap.get(prn);

            int r = startRow + (idx - 1);
            setCellValue(sheet, r, 0, idx);
            setCellValue(sheet, r, 1, prn);
            setCellValue(sheet, r, 2, name);

            for (int c = 0; c < cos.size() && c < 6; c++) {
                String coCode = cos.get(c).getCode();
                BigDecimal mark = (coMarks != null && coMarks.containsKey(coCode)) ? coMarks.get(coCode) : null;
                if (mark != null) {
                    setCellValue(sheet, r, 3 + c, mark.doubleValue());
                }
            }
            idx++;
        }
    }

    private void setCellValue(XSSFSheet sheet, int rowIdx, int colIdx, Object val) {
        if (sheet == null) return;
        XSSFRow row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        XSSFCell cell = row.getCell(colIdx);
        if (cell == null) cell = row.createCell(colIdx);

        if (val instanceof Number) {
            cell.setCellValue(((Number) val).doubleValue());
        } else if (val instanceof Boolean) {
            cell.setCellValue((Boolean) val);
        } else if (val != null) {
            cell.setCellValue(val.toString());
        } else {
            cell.setBlank();
        }
    }

    // =========================================================================
    //  PDF GENERATION ENGINE (OpenPDF)
    // =========================================================================

    /**
     * Generates a comprehensive, NBA-compliant PDF Attainment Report.
     */
    @Transactional(readOnly = true)
    public byte[] generateAttainmentPdf(String masterCourseId, String programmeBatchId) {
        System.out.println("[AttainmentReportExportService] generateAttainmentPdf called | masterCourseId: " + masterCourseId + " | programmeBatchId: " + programmeBatchId);
        log.info("[AttainmentReportExportService] Generating Attainment PDF for masterCourseId: {}, programmeBatchId: {}", masterCourseId, programmeBatchId);

        ProgrammeBatchCourse course = programmeBatchCourseRepository.findById(masterCourseId).orElse(null);
        ProgrammeBatch batch = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            batch = programmeBatchRepository.findById(programmeBatchId).orElse(null);
        }
        if (batch == null && course != null && course.getProgrammeBatchId() != null) {
            batch = programmeBatchRepository.findById(course.getProgrammeBatchId()).orElse(null);
        }

        if (course == null) {
            course = ProgrammeBatchCourse.builder()
                    .id(masterCourseId)
                    .code("COURSE-310")
                    .name("Computer Networks and Security")
                    .build();
        }

        String masterProgrammeId = (batch != null) ? batch.getMasterProgrammeId() : null;
        MasterProgramme programme = (masterProgrammeId != null)
                ? masterProgrammeRepository.findById(masterProgrammeId).orElse(null)
                : null;
        Department department = (programme != null && programme.getDepartmentId() != null)
                ? departmentRepository.findById(programme.getDepartmentId()).orElse(null)
                : null;
        School school = (department != null && department.getSchoolId() != null)
                ? schoolRepository.findById(department.getSchoolId()).orElse(null)
                : null;

        String pdfOfferingId = course.getId();

        Map<String, Object> coCalcData = calculationService.calculateCourseCoAttainment(pdfOfferingId);
        CourseMappingMatrixDto mappingsDto = outcomeService.getCourseMappings(pdfOfferingId);
        List<CourseOutcome> cos = outcomeService.getCOsByCourse(pdfOfferingId);
        List<ProgrammeOutcome> pos = (programme != null) ? outcomeService.getPOsByProgramme(programme.getId()) : Collections.emptyList();
        List<ProgrammeSpecificOutcome> psos = (programme != null) ? outcomeService.getPSOsByProgramme(programme.getId()) : Collections.emptyList();
        List<StudentCoMark> studentMarks = studentCoMarkRepository.findByProgrammeBatchCourseId(pdfOfferingId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Use Landscape A4 for rich tabular display
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PdfPageNumberHelper());
            document.open();

            // Color Palette
            Color navy = new Color(15, 23, 42);
            Color brandBlue = new Color(2, 132, 199);
            Color lightGray = new Color(241, 245, 249);
            Color borderGray = new Color(203, 213, 225);
            Color darkText = new Color(30, 41, 59);

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.WHITE);
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(224, 242, 254));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, navy);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font boldCellFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, darkText);
            Font regularCellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, darkText);
            Font smallMutedFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(100, 116, 139));

            // --- 1. Institutional Header Banner ---
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);

            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(navy);
            headerCell.setPadding(10);
            headerCell.setBorder(Rectangle.NO_BORDER);

            Paragraph pInst = new Paragraph("D Y PATIL INTERNATIONAL UNIVERSITY, AKURDI, PUNE", titleFont);
            pInst.setAlignment(Element.ALIGN_CENTER);
            headerCell.addElement(pInst);

            String schoolStr = (school != null && school.getName() != null) ? school.getName() : "School of Science and Technology";
            String deptStr = (department != null && department.getName() != null) ? department.getName() : "Computer Science & Engineering";
            Paragraph pSub = new Paragraph(schoolStr.toUpperCase() + " | DEPARTMENT OF " + deptStr.toUpperCase(), subTitleFont);
            pSub.setAlignment(Element.ALIGN_CENTER);
            headerCell.addElement(pSub);

            Paragraph pDoc = new Paragraph("COURSE OUTCOME & PROGRAMME OUTCOME ATTAINMENT REPORT", subTitleFont);
            pDoc.setAlignment(Element.ALIGN_CENTER);
            headerCell.addElement(pDoc);

            headerTable.addCell(headerCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            // --- 2. Metadata Info Card ---
            PdfPTable metaTable = new PdfPTable(4);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{25, 25, 25, 25});

            String batchName = (batch != null) ? batch.getName() : "Batch 2025-29";
            String faculty = "Course Coordinator";

            addMetaCell(metaTable, "Course Name:", course.getName(), boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Course Code:", course.getCode(), boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Academic Batch / Year:", batchName, boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Semester / Term:", "Semester 5", boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Programme:", (programme != null ? programme.getName() : "B.Tech Computer Science"), boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Course Coordinator:", faculty, boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Evaluation Weights:", "80% Direct / 20% Indirect", boldCellFont, regularCellFont);
            addMetaCell(metaTable, "Report Generated:", ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")), boldCellFont, regularCellFont);


            document.add(metaTable);
            document.add(new Paragraph(" "));

            // --- 3. Section 1: Course Outcomes ---
            Paragraph s1 = new Paragraph("1. COURSE OUTCOMES (COs)", sectionFont);
            document.add(s1);
            document.add(new Paragraph(" "));

            PdfPTable coTable = new PdfPTable(3);
            coTable.setWidthPercentage(100);
            coTable.setWidths(new float[]{10, 15, 75});

            addHeaderCell(coTable, "Sr No", brandBlue, headerFont);
            addHeaderCell(coTable, "CO Code", brandBlue, headerFont);
            addHeaderCell(coTable, "Course Outcome Statement", brandBlue, headerFont);

            for (int i = 0; i < cos.size(); i++) {
                CourseOutcome co = cos.get(i);
                addBodyCell(coTable, String.valueOf(i + 1), Element.ALIGN_CENTER, regularCellFont);
                addBodyCell(coTable, co.getCode(), Element.ALIGN_CENTER, boldCellFont);
                addBodyCell(coTable, co.getStatement(), Element.ALIGN_LEFT, regularCellFont);
            }
            document.add(coTable);
            document.add(new Paragraph(" "));

            // --- 4. Section 2: Table 1 - Combined CO to PO/PSO Mapping Matrix ---
            Paragraph s2 = new Paragraph("2. TABLE 1 : COMBINED MAPPING OF CO TO PO/PSO", sectionFont);
            document.add(s2);
            document.add(new Paragraph(" "));

            int numPos = 12;
            int numPsos = 3;
            int totalCols = 2 + numPos + numPsos; // 17 columns
            float[] mapWidths = new float[totalCols];
            mapWidths[0] = 5;  // Sr
            mapWidths[1] = 9;  // CO Code
            for (int p = 0; p < numPos; p++) mapWidths[2 + p] = 5.5f;
            for (int ps = 0; ps < numPsos; ps++) mapWidths[2 + numPos + ps] = 6.5f;

            PdfPTable mapTable = new PdfPTable(totalCols);
            mapTable.setWidthPercentage(100);
            mapTable.setWidths(mapWidths);

            addHeaderCell(mapTable, "Sr", navy, headerFont);
            addHeaderCell(mapTable, "CO Code", navy, headerFont);
            for (int p = 1; p <= numPos; p++) addHeaderCell(mapTable, "PO" + p, navy, headerFont);
            for (int ps = 1; ps <= numPsos; ps++) addHeaderCell(mapTable, "PSO" + ps, brandBlue, headerFont);

            // Mappings calculation
            Map<String, Map<String, Integer>> coPoMap = new HashMap<>();
            Map<String, Map<String, Integer>> coPsoMap = new HashMap<>();

            if (mappingsDto != null) {
                if (mappingsDto.getPoMappings() != null) {
                    for (CoPoMapping m : mappingsDto.getPoMappings()) {
                        CourseOutcome co = cos.stream().filter(c -> c.getId().equals(m.getCourseOutcomeId())).findFirst().orElse(null);
                        String coCode = co != null ? co.getCode() : null;
                        if (coCode != null && m.getPoCode() != null) {
                            coPoMap.computeIfAbsent(coCode, k -> new HashMap<>()).put(m.getPoCode(), m.getMappingLevel());
                        }
                    }
                }
                if (mappingsDto.getPsoMappings() != null) {
                    for (CoPsoMapping m : mappingsDto.getPsoMappings()) {
                        CourseOutcome co = cos.stream().filter(c -> c.getId().equals(m.getCourseOutcomeId())).findFirst().orElse(null);
                        String coCode = co != null ? co.getCode() : null;
                        if (coCode != null && m.getPsoCode() != null) {
                            coPsoMap.computeIfAbsent(coCode, k -> new HashMap<>()).put(m.getPsoCode(), m.getMappingLevel());
                        }
                    }
                }
            }

            double[] poSums = new double[numPos + 1];
            int[] poCounts = new int[numPos + 1];
            double[] psoSums = new double[numPsos + 1];
            int[] psoCounts = new int[numPsos + 1];

            for (int i = 0; i < cos.size(); i++) {
                CourseOutcome co = cos.get(i);
                String coCode = co.getCode();
                addBodyCell(mapTable, String.valueOf(i + 1), Element.ALIGN_CENTER, regularCellFont);
                addBodyCell(mapTable, coCode, Element.ALIGN_CENTER, boldCellFont);

                // PO1..PO12
                for (int p = 1; p <= numPos; p++) {
                    String poCode = "PO" + p;
                    Integer lvl = (coPoMap.containsKey(coCode)) ? coPoMap.get(coCode).get(poCode) : null;
                    if (lvl != null && lvl > 0) {
                        poSums[p] += lvl;
                        poCounts[p]++;
                        addBodyCell(mapTable, String.valueOf(lvl), Element.ALIGN_CENTER, boldCellFont);
                    } else {
                        addBodyCell(mapTable, "-", Element.ALIGN_CENTER, smallMutedFont);
                    }
                }

                // PSO1..PSO3
                for (int ps = 1; ps <= numPsos; ps++) {
                    String psoCode = "PSO" + ps;
                    Integer lvl = (coPsoMap.containsKey(coCode)) ? coPsoMap.get(coCode).get(psoCode) : null;
                    if (lvl != null && lvl > 0) {
                        psoSums[ps] += lvl;
                        psoCounts[ps]++;
                        addBodyCell(mapTable, String.valueOf(lvl), Element.ALIGN_CENTER, boldCellFont);
                    } else {
                        addBodyCell(mapTable, "-", Element.ALIGN_CENTER, smallMutedFont);
                    }
                }
            }

            // Average Row
            PdfPCell avgLabelCell = new PdfPCell(new Phrase("Average", boldCellFont));
            avgLabelCell.setColspan(2);
            avgLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            avgLabelCell.setBackgroundColor(lightGray);
            avgLabelCell.setPadding(4);
            mapTable.addCell(avgLabelCell);

            double[] poAverages = new double[numPos + 1];
            for (int p = 1; p <= numPos; p++) {
                if (poCounts[p] > 0) {
                    double avg = poSums[p] / poCounts[p];
                    poAverages[p] = avg;
                    PdfPCell c = new PdfPCell(new Phrase(String.format("%.2f", avg), boldCellFont));
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c.setBackgroundColor(lightGray);
                    c.setPadding(4);
                    mapTable.addCell(c);
                } else {
                    PdfPCell c = new PdfPCell(new Phrase("-", smallMutedFont));
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c.setBackgroundColor(lightGray);
                    c.setPadding(4);
                    mapTable.addCell(c);
                }
            }

            double[] psoAverages = new double[numPsos + 1];
            for (int ps = 1; ps <= numPsos; ps++) {
                if (psoCounts[ps] > 0) {
                    double avg = psoSums[ps] / psoCounts[ps];
                    psoAverages[ps] = avg;
                    PdfPCell c = new PdfPCell(new Phrase(String.format("%.2f", avg), boldCellFont));
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c.setBackgroundColor(new Color(237, 233, 254));
                    c.setPadding(4);
                    mapTable.addCell(c);
                } else {
                    PdfPCell c = new PdfPCell(new Phrase("-", smallMutedFont));
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    c.setBackgroundColor(new Color(237, 233, 254));
                    c.setPadding(4);
                    mapTable.addCell(c);
                }
            }

            document.add(mapTable);
            document.add(new Paragraph(" "));

            // --- 5. Section 3: CO Attainment Calculation Breakdown ---
            Paragraph s3 = new Paragraph("3. CO ATTAINMENT CALCULATION (DIRECT & INDIRECT ASSESSMENT)", sectionFont);
            document.add(s3);
            document.add(new Paragraph(" "));

            PdfPTable coAttTable = new PdfPTable(7);
            coAttTable.setWidthPercentage(100);
            coAttTable.setWidths(new float[]{10, 15, 15, 15, 15, 15, 15});

            addHeaderCell(coAttTable, "CO Code", navy, headerFont);
            addHeaderCell(coAttTable, "Direct % (Exam)", navy, headerFont);
            addHeaderCell(coAttTable, "Direct Level (80%)", navy, headerFont);
            addHeaderCell(coAttTable, "Indirect % (Survey)", navy, headerFont);
            addHeaderCell(coAttTable, "Indirect Level (20%)", navy, headerFont);
            addHeaderCell(coAttTable, "Combined Attainment", brandBlue, headerFont);
            addHeaderCell(coAttTable, "Status", brandBlue, headerFont);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> coAttainments = (List<Map<String, Object>>) coCalcData.get("coAttainments");
            BigDecimal overallCoAttainment = (BigDecimal) coCalcData.get("overallCoAttainment");
            if (overallCoAttainment == null) overallCoAttainment = BigDecimal.ZERO;

            if (coAttainments != null) {
                for (Map<String, Object> cm : coAttainments) {
                    String coCode = (String) cm.get("coCode");
                    BigDecimal directPct = (BigDecimal) cm.get("directPct");
                    Integer directLvl = (Integer) cm.get("directLevel");
                    BigDecimal indirectPct = (BigDecimal) cm.get("indirectPct");
                    Integer indirectLvl = (Integer) cm.get("indirectLevel");
                    BigDecimal combined = (BigDecimal) cm.get("combinedAttainment");

                    addBodyCell(coAttTable, coCode, Element.ALIGN_CENTER, boldCellFont);
                    addBodyCell(coAttTable, (directPct != null ? directPct + "%" : "0.00%"), Element.ALIGN_CENTER, regularCellFont);
                    addBodyCell(coAttTable, (directLvl != null ? String.valueOf(directLvl) : "0"), Element.ALIGN_CENTER, boldCellFont);
                    addBodyCell(coAttTable, (indirectPct != null ? indirectPct + "%" : "0.00%"), Element.ALIGN_CENTER, regularCellFont);
                    addBodyCell(coAttTable, (indirectLvl != null ? String.valueOf(indirectLvl) : "0"), Element.ALIGN_CENTER, boldCellFont);
                    addBodyCell(coAttTable, (combined != null ? combined.toString() : "0.00"), Element.ALIGN_CENTER, boldCellFont);
                    addBodyCell(coAttTable, (combined != null && combined.compareTo(new BigDecimal("2.00")) >= 0 ? "ATTAINED" : "NEEDS ACTION"), Element.ALIGN_CENTER, boldCellFont);
                }
            }

            document.add(coAttTable);
            document.add(new Paragraph(" "));

            // Overall Callout Card
            PdfPTable calloutTable = new PdfPTable(1);
            calloutTable.setWidthPercentage(100);
            PdfPCell calloutCell = new PdfPCell();
            calloutCell.setBackgroundColor(new Color(240, 253, 244));
            calloutCell.setBorderColor(new Color(134, 239, 172));
            calloutCell.setPadding(8);

            Paragraph pScore = new Paragraph("OVERALL COURSE CO ATTAINMENT SCORE: " + overallCoAttainment + " / 3.00 (Benchmark Attainment Level: 2.00)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(22, 101, 52)));
            pScore.setAlignment(Element.ALIGN_CENTER);
            calloutCell.addElement(pScore);
            calloutTable.addCell(calloutCell);
            document.add(calloutTable);

            document.add(new Paragraph(" "));

            // --- 6. Section 4: Table 2 - PO Attainment Values (Direct Attainment) ---
            Paragraph s4 = new Paragraph("4. TABLE 2 : PO & PSO ATTAINMENT VALUES (DIRECT ATTAINMENT)", sectionFont);
            document.add(s4);
            document.add(new Paragraph(" "));

            PdfPTable poAttTable = new PdfPTable(totalCols - 1);
            poAttTable.setWidthPercentage(100);

            float[] poWidths = new float[totalCols - 1];
            poWidths[0] = 12; // Course Code
            for (int p = 0; p < numPos; p++) poWidths[1 + p] = 5.5f;
            for (int ps = 0; ps < numPsos; ps++) poWidths[1 + numPos + ps] = 6.5f;
            poAttTable.setWidths(poWidths);

            addHeaderCell(poAttTable, "Course Code", navy, headerFont);
            for (int p = 1; p <= numPos; p++) addHeaderCell(poAttTable, "PO" + p, navy, headerFont);
            for (int ps = 1; ps <= numPsos; ps++) addHeaderCell(poAttTable, "PSO" + ps, brandBlue, headerFont);

            addBodyCell(poAttTable, course.getCode(), Element.ALIGN_CENTER, boldCellFont);

            double overallDouble = overallCoAttainment.doubleValue();

            for (int p = 1; p <= numPos; p++) {
                if (poCounts[p] > 0) {
                    double val = (poAverages[p] * overallDouble) / 3.0;
                    addBodyCell(poAttTable, String.format("%.2f", val), Element.ALIGN_CENTER, boldCellFont);
                } else {
                    addBodyCell(poAttTable, "-", Element.ALIGN_CENTER, smallMutedFont);
                }
            }

            for (int ps = 1; ps <= numPsos; ps++) {
                if (psoCounts[ps] > 0) {
                    double val = (psoAverages[ps] * overallDouble) / 3.0;
                    addBodyCell(poAttTable, String.format("%.2f", val), Element.ALIGN_CENTER, boldCellFont);
                } else {
                    addBodyCell(poAttTable, "-", Element.ALIGN_CENTER, smallMutedFont);
                }
            }

            document.add(poAttTable);
            document.add(new Paragraph(" "));

            // --- 7. Section 5: Signatures / Approvals Block ---
            PdfPTable signTable = new PdfPTable(4);
            signTable.setWidthPercentage(100);
            signTable.setWidths(new float[]{25, 25, 25, 25});

            addSignCell(signTable, "Course Coordinator", faculty);
            addSignCell(signTable, "Programme Coordinator", "Programme Coordinator");
            addSignCell(signTable, "Head of Department (HOD)", "Head of Department");
            addSignCell(signTable, "Director / Dean", "Director of School");

            document.add(signTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating Attainment PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Attainment PDF report: " + e.getMessage(), e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Color bgColor, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(new Color(203, 213, 225));
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private void addMetaCell(PdfPTable table, String label, String val, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(5);
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setBackgroundColor(new Color(248, 250, 252));

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", labelFont));
        p.add(new Chunk(val != null ? val : "N/A", valFont));
        cell.addElement(p);

        table.addCell(cell);
    }

    private void addSignCell(PdfPTable table, String role, String name) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(14);
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pSpace = new Paragraph("\n\n");
        Paragraph pRole = new Paragraph(role, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(15, 23, 42)));
        pRole.setAlignment(Element.ALIGN_CENTER);
        Paragraph pName = new Paragraph(name != null ? "(" + name + ")" : "(Signature)", FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(100, 116, 139)));
        pName.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(pSpace);
        cell.addElement(pRole);
        cell.addElement(pName);

        table.addCell(cell);
    }

    /**
     * Generates a multi-sheet Programme Batch Attainment Excel matching the official NBA format.
     */
    @Transactional(readOnly = true)
    public byte[] generateProgrammeBatchExcel(String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AttainmentReportExportService] generateProgrammeBatchExcel called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        log.info("[AttainmentReportExportService] Generating Programme Batch Attainment Excel for masterProgrammeId: {}, programmeBatchId: {}", masterProgrammeId, programmeBatchId);

        MasterProgramme prog = (masterProgrammeId != null && !masterProgrammeId.isBlank())
                ? masterProgrammeRepository.findById(masterProgrammeId).orElse(null)
                : null;
        ProgrammeBatch batch = (programmeBatchId != null && !programmeBatchId.isBlank())
                ? programmeBatchRepository.findById(programmeBatchId).orElse(null)
                : null;

        if (prog == null && batch != null && batch.getMasterProgrammeId() != null) {
            prog = masterProgrammeRepository.findById(batch.getMasterProgrammeId()).orElse(null);
        }
        String pId = prog != null ? prog.getId() : masterProgrammeId;
        String bId = batch != null ? batch.getId() : programmeBatchId;

        ProgrammeAttainmentResultDto res = calculationService.calculateProgrammeAttainment(pId, bId);

        Department department = (prog != null && prog.getDepartmentId() != null)
                ? departmentRepository.findById(prog.getDepartmentId()).orElse(null)
                : null;
        School school = (department != null && department.getSchoolId() != null)
                ? schoolRepository.findById(department.getSchoolId()).orElse(null)
                : null;

        String schoolName = (school != null && school.getName() != null) ? school.getName() : "School of Engineering and Technology";
        String deptName = (department != null && department.getName() != null) ? department.getName() : (prog != null ? prog.getName() : "Computer Engineering");
        String batchYear = (batch != null && batch.getStartYear() != null && batch.getEndYear() != null)
                ? batch.getStartYear() + "-" + batch.getEndYear()
                : (batch != null ? batch.getName() : "2024-2028");

        List<String> poCodes = new ArrayList<>();
        if (res.getAverageMapping() != null && res.getAverageMapping().getPos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeMappingItem item : res.getAverageMapping().getPos()) {
                if (item.getPoCode() != null) poCodes.add(item.getPoCode());
            }
        }
        List<String> psoCodes = new ArrayList<>();
        if (res.getAverageMapping() != null && res.getAverageMapping().getPsos() != null) {
            for (ProgrammeAttainmentResultDto.OutcomeMappingItem item : res.getAverageMapping().getPsos()) {
                if (item.getPsoCode() != null) psoCodes.add(item.getPsoCode());
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Font bold12 = wb.createFont();
            bold12.setBold(true);
            bold12.setFontHeightInPoints((short) 12);

            org.apache.poi.ss.usermodel.Font bold10 = wb.createFont();
            bold10.setBold(true);
            bold10.setFontHeightInPoints((short) 10);

            org.apache.poi.ss.usermodel.Font normal10 = wb.createFont();
            normal10.setFontHeightInPoints((short) 10);

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(bold12);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle subTitleStyle = wb.createCellStyle();
            subTitleStyle.setFont(bold10);
            subTitleStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold10);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle semHeaderStyle = wb.createCellStyle();
            semHeaderStyle.setFont(bold10);
            semHeaderStyle.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            semHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            semHeaderStyle.setBorderTop(BorderStyle.THIN);
            semHeaderStyle.setBorderBottom(BorderStyle.THIN);
            semHeaderStyle.setBorderLeft(BorderStyle.THIN);
            semHeaderStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = wb.createCellStyle();
            dataStyle.setFont(normal10);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataLeftStyle = wb.createCellStyle();
            dataLeftStyle.setFont(normal10);
            dataLeftStyle.setAlignment(HorizontalAlignment.LEFT);
            dataLeftStyle.setBorderTop(BorderStyle.THIN);
            dataLeftStyle.setBorderBottom(BorderStyle.THIN);
            dataLeftStyle.setBorderLeft(BorderStyle.THIN);
            dataLeftStyle.setBorderRight(BorderStyle.THIN);

            CellStyle summaryStyle = wb.createCellStyle();
            summaryStyle.setFont(bold10);
            summaryStyle.setAlignment(HorizontalAlignment.CENTER);
            summaryStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            summaryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            summaryStyle.setBorderTop(BorderStyle.THIN);
            summaryStyle.setBorderBottom(BorderStyle.DOUBLE);
            summaryStyle.setBorderLeft(BorderStyle.THIN);
            summaryStyle.setBorderRight(BorderStyle.THIN);

            // ==========================================
            // SHEET 1: Average Mapping
            // ==========================================
            XSSFSheet sheet1 = wb.createSheet("Average Mapping");
            buildProgrammeCourseGridSheet(sheet1, "Average Mapping Strength", schoolName, deptName, batchYear,
                    poCodes, psoCodes, res.getCourseMappingRows(), res.getAverageMapping(),
                    titleStyle, subTitleStyle, headerStyle, semHeaderStyle, dataStyle, dataLeftStyle, summaryStyle);

            // ==========================================
            // SHEET 2: Average Attainment(D)
            // ==========================================
            XSSFSheet sheet2 = wb.createSheet("Average Attainment(D)");
            buildProgrammeCourseGridSheet(sheet2, "PO & PSO Attainment (Direct)", schoolName, deptName, batchYear,
                    poCodes, psoCodes, res.getCourseDirectAttainmentRows(), res.getAverageDirectAttainment(),
                    titleStyle, subTitleStyle, headerStyle, semHeaderStyle, dataStyle, dataLeftStyle, summaryStyle);

            // ==========================================
            // SHEET 3: Average Attainment(ID)
            // ==========================================
            XSSFSheet sheet3 = wb.createSheet("Average Attainment(ID)");
            buildIndirectSurveySheet(sheet3, schoolName, deptName, batchYear, poCodes, psoCodes, res.getAverageIndirectAttainment(),
                    titleStyle, subTitleStyle, headerStyle, dataStyle, summaryStyle);

            // ==========================================
            // SHEET 4: Overall-attainment
            // ==========================================
            XSSFSheet sheet4 = wb.createSheet("Overall-attainment");
            buildOverallAttainmentSheet(sheet4, schoolName, deptName, batchYear, poCodes, psoCodes, res,
                    titleStyle, subTitleStyle, headerStyle, dataStyle, dataLeftStyle, summaryStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[AttainmentReportExportService] Failed to generate Programme Batch Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Programme Batch Excel: " + e.getMessage(), e);
        }
    }

    private void buildProgrammeCourseGridSheet(XSSFSheet sheet, String sheetTitle, String schoolName, String deptName, String batchYear,
                                                List<String> poCodes, List<String> psoCodes,
                                                List<ProgrammeAttainmentResultDto.CourseContributionRow> courseRows,
                                                Object outcomeBreakdown,
                                                CellStyle titleStyle, CellStyle subTitleStyle, CellStyle headerStyle,
                                                CellStyle semHeaderStyle, CellStyle dataStyle, CellStyle dataLeftStyle, CellStyle summaryStyle) {
        int rowNum = 0;

        Row r0 = sheet.createRow(rowNum++);
        Cell c0 = r0.createCell(2);
        c0.setCellValue("D Y Patil International University, Akurdi Pune");
        c0.setCellStyle(titleStyle);

        Row r1 = sheet.createRow(rowNum++);
        Cell c1 = r1.createCell(2);
        c1.setCellValue(schoolName);
        c1.setCellStyle(subTitleStyle);

        Row r2 = sheet.createRow(rowNum++);
        Cell c2a = r2.createCell(0);
        c2a.setCellValue("Academic Year: " + batchYear);
        c2a.setCellStyle(subTitleStyle);
        Cell c2b = r2.createCell(2);
        c2b.setCellValue(sheetTitle);
        c2b.setCellStyle(subTitleStyle);

        Row r3 = sheet.createRow(rowNum++);
        Cell c3a = r3.createCell(0);
        c3a.setCellValue("Term – I & II");
        c3a.setCellStyle(subTitleStyle);
        Cell c3b = r3.createCell(2);
        c3b.setCellValue("Department : " + deptName);
        c3b.setCellStyle(subTitleStyle);

        rowNum++; // blank row

        // Headers row
        Row hRow = sheet.createRow(rowNum++);
        int colIdx = 0;
        createCell(hRow, colIdx++, "Sem", headerStyle);
        createCell(hRow, colIdx++, "Course Code", headerStyle);
        createCell(hRow, colIdx++, "Course Name", headerStyle);
        createCell(hRow, colIdx++, "Course No", headerStyle);

        for (String po : poCodes) {
            createCell(hRow, colIdx++, po, headerStyle);
        }
        for (String pso : psoCodes) {
            createCell(hRow, colIdx++, pso, headerStyle);
        }

        // Group courses by semester
        Map<Integer, List<ProgrammeAttainmentResultDto.CourseContributionRow>> semMap = new TreeMap<>();
        if (courseRows != null) {
            for (ProgrammeAttainmentResultDto.CourseContributionRow cr : courseRows) {
                int sem = (cr.getSemester() != null) ? cr.getSemester() : 1;
                semMap.computeIfAbsent(sem, k -> new ArrayList<>()).add(cr);
            }
        }

        for (Map.Entry<Integer, List<ProgrammeAttainmentResultDto.CourseContributionRow>> entry : semMap.entrySet()) {
            int sem = entry.getKey();
            Row sHeader = sheet.createRow(rowNum++);
            createCell(sHeader, 0, "Sem - " + sem, semHeaderStyle);
            for (int i = 1; i < colIdx; i++) {
                createCell(sHeader, i, "", semHeaderStyle);
            }

            for (ProgrammeAttainmentResultDto.CourseContributionRow cr : entry.getValue()) {
                Row cRow = sheet.createRow(rowNum++);
                int cCol = 0;
                createCell(cRow, cCol++, "", dataStyle);
                createCell(cRow, cCol++, cr.getCourseCode() != null ? cr.getCourseCode() : "", dataStyle);
                createCell(cRow, cCol++, cr.getCourseName() != null ? cr.getCourseName() : "", dataLeftStyle);
                createCell(cRow, cCol++, cr.getCourseNo() != null ? cr.getCourseNo() : "", dataStyle);

                for (String po : poCodes) {
                    BigDecimal v = (cr.getPoValues() != null) ? cr.getPoValues().get(po.toUpperCase()) : null;
                    createCell(cRow, cCol++, (v != null && v.compareTo(BigDecimal.ZERO) > 0) ? v.toString() : "-", dataStyle);
                }
                for (String pso : psoCodes) {
                    BigDecimal v = (cr.getPsoValues() != null) ? cr.getPsoValues().get(pso.toUpperCase()) : null;
                    createCell(cRow, cCol++, (v != null && v.compareTo(BigDecimal.ZERO) > 0) ? v.toString() : "-", dataStyle);
                }
            }
        }

        // Bottom Summary Row
        Row sumRow = sheet.createRow(rowNum++);
        createCell(sumRow, 0, sheetTitle, summaryStyle);
        createCell(sumRow, 1, "", summaryStyle);
        createCell(sumRow, 2, "", summaryStyle);
        createCell(sumRow, 3, "", summaryStyle);

        int sumCol = 4;
        Map<String, BigDecimal> poSummaryMap = new HashMap<>();
        Map<String, BigDecimal> psoSummaryMap = new HashMap<>();

        if (outcomeBreakdown instanceof ProgrammeAttainmentResultDto.MappingBreakdown mb) {
            if (mb.getPos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : mb.getPos()) {
                    if (it.getPoCode() != null) poSummaryMap.put(it.getPoCode().toUpperCase(), it.getOverallAverage());
                }
            }
            if (mb.getPsos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : mb.getPsos()) {
                    if (it.getPsoCode() != null) psoSummaryMap.put(it.getPsoCode().toUpperCase(), it.getOverallAverage());
                }
            }
        } else if (outcomeBreakdown instanceof ProgrammeAttainmentResultDto.DirectAttainmentBreakdown db) {
            if (db.getPos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : db.getPos()) {
                    if (it.getPoCode() != null) poSummaryMap.put(it.getPoCode().toUpperCase(), it.getOverallAverage());
                }
            }
            if (db.getPsos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : db.getPsos()) {
                    if (it.getPsoCode() != null) psoSummaryMap.put(it.getPsoCode().toUpperCase(), it.getOverallAverage());
                }
            }
        }

        for (String po : poCodes) {
            BigDecimal val = poSummaryMap.getOrDefault(po.toUpperCase(), BigDecimal.ZERO);
            createCell(sumRow, sumCol++, val != null ? val.toString() : "0.00", summaryStyle);
        }
        for (String pso : psoCodes) {
            BigDecimal val = psoSummaryMap.getOrDefault(pso.toUpperCase(), BigDecimal.ZERO);
            createCell(sumRow, sumCol++, val != null ? val.toString() : "0.00", summaryStyle);
        }

        for (int i = 0; i < colIdx; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildIndirectSurveySheet(XSSFSheet sheet, String schoolName, String deptName, String batchYear,
                                           List<String> poCodes, List<String> psoCodes,
                                           Map<String, BigDecimal> indirectMap,
                                           CellStyle titleStyle, CellStyle subTitleStyle, CellStyle headerStyle,
                                           CellStyle dataStyle, CellStyle summaryStyle) {
        int rowNum = 0;

        Row r0 = sheet.createRow(rowNum++);
        Cell c0 = r0.createCell(2);
        c0.setCellValue("D Y Patil International University, Akurdi Pune");
        c0.setCellStyle(titleStyle);

        Row r1 = sheet.createRow(rowNum++);
        Cell c1 = r1.createCell(2);
        c1.setCellValue(schoolName);
        c1.setCellStyle(subTitleStyle);

        Row r2 = sheet.createRow(rowNum++);
        Cell c2a = r2.createCell(0);
        c2a.setCellValue("Academic Year: " + batchYear);
        c2a.setCellStyle(subTitleStyle);
        Cell c2b = r2.createCell(2);
        c2b.setCellValue("PO & PSO Attainment (Indirect) - Exit Survey");
        c2b.setCellStyle(subTitleStyle);

        rowNum++; // blank row

        Row hRow = sheet.createRow(rowNum++);
        int colIdx = 0;
        createCell(hRow, colIdx++, "Parameter", headerStyle);
        for (String po : poCodes) createCell(hRow, colIdx++, po, headerStyle);
        for (String pso : psoCodes) createCell(hRow, colIdx++, pso, headerStyle);

        Row dRow = sheet.createRow(rowNum++);
        int dCol = 0;
        createCell(dRow, dCol++, "Indirect Attainment Level", summaryStyle);
        for (String po : poCodes) {
            BigDecimal v = (indirectMap != null) ? indirectMap.getOrDefault(po.toUpperCase(), BigDecimal.ZERO) : BigDecimal.ZERO;
            createCell(dRow, dCol++, v != null ? v.toString() : "0.00", summaryStyle);
        }
        for (String pso : psoCodes) {
            BigDecimal v = (indirectMap != null) ? indirectMap.getOrDefault(pso.toUpperCase(), BigDecimal.ZERO) : BigDecimal.ZERO;
            createCell(dRow, dCol++, v != null ? v.toString() : "0.00", summaryStyle);
        }

        for (int i = 0; i < colIdx; i++) sheet.autoSizeColumn(i);
    }

    private void buildOverallAttainmentSheet(XSSFSheet sheet, String schoolName, String deptName, String batchYear,
                                              List<String> poCodes, List<String> psoCodes,
                                              ProgrammeAttainmentResultDto res,
                                              CellStyle titleStyle, CellStyle subTitleStyle, CellStyle headerStyle,
                                              CellStyle dataStyle, CellStyle dataLeftStyle, CellStyle summaryStyle) {
        int rowNum = 0;

        Row r0 = sheet.createRow(rowNum++);
        Cell c0 = r0.createCell(2);
        c0.setCellValue("D Y Patil International University, Akurdi Pune");
        c0.setCellStyle(titleStyle);

        Row r1 = sheet.createRow(rowNum++);
        Cell c1 = r1.createCell(2);
        c1.setCellValue(schoolName);
        c1.setCellStyle(subTitleStyle);

        Row r2 = sheet.createRow(rowNum++);
        Cell c2a = r2.createCell(0);
        c2a.setCellValue("Academic Year: " + batchYear);
        c2a.setCellStyle(subTitleStyle);
        Cell c2b = r2.createCell(2);
        c2b.setCellValue("Overall Attainment Matrix (Direct 80% + Indirect 20%)");
        c2b.setCellStyle(subTitleStyle);

        rowNum++; // blank row

        Row hRow = sheet.createRow(rowNum++);
        int colIdx = 0;
        createCell(hRow, colIdx++, "Evaluation Metric", headerStyle);
        for (String po : poCodes) createCell(hRow, colIdx++, po, headerStyle);
        for (String pso : psoCodes) createCell(hRow, colIdx++, pso, headerStyle);

        Map<String, BigDecimal> mapValues = new LinkedHashMap<>();
        if (res.getAverageMapping() != null) {
            if (res.getAverageMapping().getPos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : res.getAverageMapping().getPos()) {
                    if (it.getPoCode() != null) mapValues.put(it.getPoCode().toUpperCase(), it.getOverallAverage());
                }
            }
            if (res.getAverageMapping().getPsos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeMappingItem it : res.getAverageMapping().getPsos()) {
                    if (it.getPsoCode() != null) mapValues.put(it.getPsoCode().toUpperCase(), it.getOverallAverage());
                }
            }
        }

        Map<String, BigDecimal> dirValues = new LinkedHashMap<>();
        if (res.getAverageDirectAttainment() != null) {
            if (res.getAverageDirectAttainment().getPos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : res.getAverageDirectAttainment().getPos()) {
                    if (it.getPoCode() != null) dirValues.put(it.getPoCode().toUpperCase(), it.getOverallAverage());
                }
            }
            if (res.getAverageDirectAttainment().getPsos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeDirectItem it : res.getAverageDirectAttainment().getPsos()) {
                    if (it.getPsoCode() != null) dirValues.put(it.getPsoCode().toUpperCase(), it.getOverallAverage());
                }
            }
        }

        Map<String, BigDecimal> indValues = (res.getAverageIndirectAttainment() != null) ? res.getAverageIndirectAttainment() : Collections.emptyMap();

        Map<String, BigDecimal> finalValues = new LinkedHashMap<>();
        Map<String, BigDecimal> targetValues = new LinkedHashMap<>();
        Map<String, String> obsValues = new LinkedHashMap<>();

        if (res.getOverallAttainment() != null) {
            if (res.getOverallAttainment().getPos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPos()) {
                    String c = it.getPoCode() != null ? it.getPoCode().toUpperCase() : (it.getOutcomeCode() != null ? it.getOutcomeCode().toUpperCase() : "");
                    finalValues.put(c, it.getOverallAttainment());
                    targetValues.put(c, it.getTarget());
                    obsValues.put(c, it.getObservation());
                }
            }
            if (res.getOverallAttainment().getPsos() != null) {
                for (ProgrammeAttainmentResultDto.OutcomeAttainmentItem it : res.getOverallAttainment().getPsos()) {
                    String c = it.getPsoCode() != null ? it.getPsoCode().toUpperCase() : (it.getOutcomeCode() != null ? it.getOutcomeCode().toUpperCase() : "");
                    finalValues.put(c, it.getOverallAttainment());
                    targetValues.put(c, it.getTarget());
                    obsValues.put(c, it.getObservation());
                }
            }
        }

        // Row 1: Average Mapping Values
        Row rowMap = sheet.createRow(rowNum++);
        int cM = 0;
        createCell(rowMap, cM++, "Average Mapping Values", dataLeftStyle);
        for (String po : poCodes) createCell(rowMap, cM++, formatVal(mapValues.get(po.toUpperCase())), dataStyle);
        for (String pso : psoCodes) createCell(rowMap, cM++, formatVal(mapValues.get(pso.toUpperCase())), dataStyle);

        // Row 2: Average Attainment (Direct)
        Row rowDir = sheet.createRow(rowNum++);
        int cD = 0;
        createCell(rowDir, cD++, "Average Attainment (Direct)", dataLeftStyle);
        for (String po : poCodes) createCell(rowDir, cD++, formatVal(dirValues.get(po.toUpperCase())), dataStyle);
        for (String pso : psoCodes) createCell(rowDir, cD++, formatVal(dirValues.get(pso.toUpperCase())), dataStyle);

        // Row 3: Average Attainment (Indirect)
        Row rowInd = sheet.createRow(rowNum++);
        int cI = 0;
        createCell(rowInd, cI++, "Average Attainment (Indirect)", dataLeftStyle);
        for (String po : poCodes) createCell(rowInd, cI++, formatVal(indValues.get(po.toUpperCase())), dataStyle);
        for (String pso : psoCodes) createCell(rowInd, cI++, formatVal(indValues.get(pso.toUpperCase())), dataStyle);

        // Row 4: Overall Attainment (80% Direct + 20% Indirect)
        Row rowFinal = sheet.createRow(rowNum++);
        int cF = 0;
        createCell(rowFinal, cF++, "Overall Attainment (80% Direct + 20% Indirect)", summaryStyle);
        for (String po : poCodes) createCell(rowFinal, cF++, formatVal(finalValues.get(po.toUpperCase())), summaryStyle);
        for (String pso : psoCodes) createCell(rowFinal, cF++, formatVal(finalValues.get(pso.toUpperCase())), summaryStyle);

        // Row 5: Target Benchmark
        Row rowTarget = sheet.createRow(rowNum++);
        int cT = 0;
        createCell(rowTarget, cT++, "Target Benchmark", dataLeftStyle);
        for (String po : poCodes) createCell(rowTarget, cT++, formatVal(targetValues.get(po.toUpperCase())), dataStyle);
        for (String pso : psoCodes) createCell(rowTarget, cT++, formatVal(targetValues.get(pso.toUpperCase())), dataStyle);

        // Row 6: Observations
        Row rowObs = sheet.createRow(rowNum++);
        int cO = 0;
        createCell(rowObs, cO++, "Observation & Target Met", dataLeftStyle);
        for (String po : poCodes) createCell(rowObs, cO++, obsValues.getOrDefault(po.toUpperCase(), "-"), dataStyle);
        for (String pso : psoCodes) createCell(rowObs, cO++, obsValues.getOrDefault(pso.toUpperCase(), "-"), dataStyle);

        for (int i = 0; i < colIdx; i++) sheet.autoSizeColumn(i);
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private String formatVal(BigDecimal val) {
        return (val != null && val.compareTo(BigDecimal.ZERO) > 0) ? val.setScale(2, RoundingMode.HALF_UP).toString() : "0.00";
    }

    // Page Event for numbering
    private static class PdfPageNumberHelper extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase(
                    String.format("DYPIU NBA Attainment Engine  |  Page %d  |  Confidential & Internal Accreditation Record", writer.getPageNumber()),
                    FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(148, 163, 184))
            );
            ColumnText.showTextAligned(
                    cb,
                    Element.ALIGN_CENTER,
                    footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 10,
                    0
            );
        }
    }
}
