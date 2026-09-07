package com.dypiu.nba.audit;

import com.dypiu.nba.dto.AuditLogPageResponseDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AcademicService academicService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private OutcomeService outcomeService;

    @Autowired
    private AtrService atrService;

    @Autowired
    private AttainmentCalculationService attainmentCalculationService;

    private User testAdmin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        testAdmin = userRepository.save(User.builder()
                .email("iqac.audit@dypiu.ac.in")
                .username("iqac_audit")
                .name("Audit IQAC")
                .passwordHash("secret_hash")
                .role(UserRole.IQAC)
                .isActive(true)
                .build());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                testAdmin.getEmail(), "credentials", List.of(new SimpleGrantedAuthority("ROLE_IQAC"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testAuditLogCreationWithDerivedActor() {
        AuditLog entry = auditLogService.recordSuccess(
                AuditAction.CREATE,
                ResourceType.MASTER_PROGRAMME,
                "prog-test-01",
                null,
                "ACTIVE",
                "Created B.Tech CSE catalogue",
                Map.of("code", "BTECH-CSE", "durationYears", 4)
        );

        assertNotNull(entry.getId());
        assertEquals(String.valueOf(testAdmin.getId()), entry.getActorId());
        assertEquals("IQAC", entry.getActorRole());
        assertEquals("Audit IQAC", entry.getActorName());
        assertEquals("iqac.audit@dypiu.ac.in", entry.getActorEmail());
        assertEquals(AuditAction.CREATE, entry.getAction());
        assertEquals(ResourceType.MASTER_PROGRAMME, entry.getResourceType());
        assertEquals("prog-test-01", entry.getResourceId());
        assertTrue(entry.isSuccess());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void testSensitiveDataSanitizationInMetadataAndRemarks() {
        Map<String, Object> sensitiveData = Map.of(
                "username", "john_doe",
                "password", "secretPassword123",
                "token", "eyJhbGciOiJIUzI1NiIsIn...",
                "refreshToken", "dypiu_refresh_token_99",
                "nonSensitiveKey", "safeValue"
        );

        AuditLog entry = auditLogService.recordSuccess(
                AuditAction.UPDATE,
                ResourceType.USER,
                "user-101",
                "ACTIVE",
                "ACTIVE",
                "Updated user password",
                sensitiveData
        );

        assertNotNull(entry.getMetadata());
        assertFalse(entry.getMetadata().contains("secretPassword123"));
        assertFalse(entry.getMetadata().contains("eyJhbGciOiJIUzI1NiIsIn..."));
        assertFalse(entry.getMetadata().contains("dypiu_refresh_token_99"));
        assertTrue(entry.getMetadata().contains("[REDACTED]"));
        assertTrue(entry.getMetadata().contains("safeValue"));
    }

    @Test
    void testApprovalTransitionAuditing() {
        AuditLog submitLog = auditLogService.recordSuccess(
                AuditAction.SUBMIT,
                ResourceType.APPROVAL_REQUEST,
                "app-100",
                "DRAFT",
                "PENDING",
                "Submitted for PC approval",
                Map.of("type", "CO_DEFINITION")
        );

        AuditLog approveLog = auditLogService.recordSuccess(
                AuditAction.APPROVE,
                ResourceType.APPROVAL_REQUEST,
                "app-100",
                "PENDING",
                "APPROVED",
                "Approved after review",
                Map.of("type", "CO_DEFINITION")
        );

        assertEquals("DRAFT", submitLog.getOldStatus());
        assertEquals("PENDING", submitLog.getNewStatus());
        assertEquals("PENDING", approveLog.getOldStatus());
        assertEquals("APPROVED", approveLog.getNewStatus());
    }

    @Test
    void testFailureAuditing() {
        AuditLog failLog = auditLogService.recordFailure(
                AuditAction.SUBMIT,
                ResourceType.COURSE_ATR,
                "off-failed-1",
                "DRAFT",
                "DRAFT",
                "Validation error: Missing action item for CO3",
                Map.of("error", "Validation failed")
        );

        assertFalse(failLog.isSuccess());
        assertEquals(AuditAction.SUBMIT, failLog.getAction());
        assertEquals(ResourceType.COURSE_ATR, failLog.getResourceType());
    }

    @Test
    void testAuditLogPaginationAndFiltering() {
        for (int i = 1; i <= 15; i++) {
            auditLogService.recordSuccess(
                    AuditAction.CREATE,
                    ResourceType.MASTER_COURSE,
                    "crs-" + i,
                    null,
                    "ACTIVE",
                    "Created course " + i,
                    Map.of("courseIndex", i)
            );
        }

        AuditLogPageResponseDto page1 = auditLogService.getAuditLogs(
                null, null, AuditAction.CREATE, ResourceType.MASTER_COURSE, null, true, null, null, 0, 10
        );

        assertEquals(10, page1.getContent().size());
        assertEquals(15, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertFalse(page1.isLast());

        AuditLogPageResponseDto page2 = auditLogService.getAuditLogs(
                null, null, AuditAction.CREATE, ResourceType.MASTER_COURSE, null, true, null, null, 1, 10
        );

        assertEquals(5, page2.getContent().size());
        assertTrue(page2.isLast());
    }

    @Test
    void testAcademicServiceMutationsEmitAuditLogs() {
        School school = academicService.saveSchool(School.builder()
                .code("SOE-AUDIT")
                .name("School of Engineering Audit")
                .build());

        Department dept = academicService.saveDepartment(Department.builder()
                .schoolId(school.getId())
                .code("CSE-AUDIT")
                .name("Computer Science Audit")
                .build());

        MasterProgramme prog = academicService.saveProgramme(MasterProgramme.builder()
                .departmentId(dept.getId())
                .code("BTECH-CS-AUDIT")
                .name("B.Tech CS Audit")
                .build());

        ProgrammeBatch batch = academicService.saveBatch(ProgrammeBatch.builder()
                .masterProgrammeId(prog.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        ProgrammeBatchCourse course = academicService.saveCourse(ProgrammeBatchCourse.builder()
                .masterProgrammeId(prog.getId())
                .programmeBatchId(batch.getId()).semester(1).code("CS101-AUDIT")
                .name("Intro to CS")
                .credits(4)
                .build());

        ProgrammeBatchCourse offering = academicService.saveProgrammeBatchCourse(ProgrammeBatchCourse.builder()
                .programmeBatchId(batch.getId())
                .masterCourseId(course.getId())
                .semester(1)
                .build());

        List<AuditLog> schoolLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(school.getId());
        assertFalse(schoolLogs.isEmpty());
        assertEquals(ResourceType.SCHOOL, schoolLogs.get(0).getResourceType());

        List<AuditLog> progLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(prog.getId());
        assertFalse(progLogs.isEmpty());
        assertEquals(ResourceType.MASTER_PROGRAMME, progLogs.get(0).getResourceType());

        List<AuditLog> batchLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(batch.getId());
        assertFalse(batchLogs.isEmpty());
        assertEquals(ResourceType.PROGRAMME_BATCH, batchLogs.get(0).getResourceType());

        List<AuditLog> courseLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(course.getId());
        assertFalse(courseLogs.isEmpty());
        assertEquals(ResourceType.PROGRAMME_BATCH_COURSE, courseLogs.get(0).getResourceType());

        List<AuditLog> offeringLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(offering.getId());
        assertFalse(offeringLogs.isEmpty());
        assertEquals(ResourceType.PROGRAMME_BATCH_COURSE, offeringLogs.get(0).getResourceType());
    }
}
