package com.dypiu.nba.deletion;

import com.dypiu.nba.audit.AuditAction;
import com.dypiu.nba.audit.ResourceType;
import com.dypiu.nba.dto.*;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class DeletionWorkflowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DeletionRequestRepository deletionRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private MasterProgrammeRepository masterProgrammeRepository;

    @Autowired
    private ProgrammeBatchRepository programmeBatchRepository;
@Autowired
    private ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User iqac;
    private User director;
    private User otherDirector;
    private User hod;
    private User otherHod;
    private User pc;
    private User faculty;

    private String iqacToken;
    private String directorToken;
    private String otherDirectorToken;
    private String hodToken;
    private String otherHodToken;
    private String pcToken;
    private String facultyToken;

    private School school;
    private School otherSchool;
    private Department department;
    private Department otherDept;
    private MasterProgramme programme;
    private ProgrammeBatch batch;
    private ProgrammeBatchCourse course;
    private ProgrammeBatchCourse batchCourse;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        deletionRequestRepository.deleteAll();
        auditLogRepository.deleteAll();
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("DELETE FROM programme_batch_courses");
            jdbcTemplate.execute("DELETE FROM programme_batches");
            jdbcTemplate.execute("DELETE FROM master_programmes");
        } else {
            programmeBatchCourseRepository.deleteAll();
            programmeBatchRepository.deleteAll();
            masterProgrammeRepository.deleteAll();
        }
        departmentRepository.deleteAll();
        schoolRepository.deleteAll();
        userRepository.deleteAll();

        school = schoolRepository.save(School.builder()
                .id("sch-soe-del")
                .code("SOE-DEL")
                .name("School of Engineering Deletion")
                .build());

        otherSchool = schoolRepository.save(School.builder()
                .id("sch-som-del")
                .code("SOM-DEL")
                .name("School of Management Deletion")
                .build());

        department = departmentRepository.save(Department.builder()
                .id("dept-cse-del")
                .schoolId(school.getId())
                .code("CSE-DEL")
                .name("Computer Science Deletion")
                .build());

        otherDept = departmentRepository.save(Department.builder()
                .id("dept-ece-del")
                .schoolId(school.getId())
                .code("ECE-DEL")
                .name("Electronics Deletion")
                .build());

        programme = masterProgrammeRepository.save(MasterProgramme.builder()
                .id("prog-btech-del")
                .departmentId(department.getId())
                .code("BTECH-CSE-DEL")
                .name("B.Tech CSE Deletion")
                .durationYears(4)
                .build());

        batch = programmeBatchRepository.save(ProgrammeBatch.builder()
                .id("batch-2024-del")
                .masterProgrammeId(programme.getId())
                .name("2024-2028")
                .startYear(2024)
                .endYear(2028)
                .build());

        course = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder().programmeBatchId(batch.getId()).semester(1)
                .id("crs-cs101-del")
                .masterProgrammeId(programme.getId())
                .code("CS101-DEL")
                .name("Intro to CS")
                .credits(4)
                .build());

        batchCourse = programmeBatchCourseRepository.save(ProgrammeBatchCourse.builder()
                .id("off-cs101-del")
                .programmeBatchId(batch.getId())
                .code(course.getCode()).name(course.getName()).credits(course.getCredits())
                .semester(1)
                .build());

        iqac = createUser("iqac.del@dypiu.ac.in", "iqac_del", UserRole.IQAC, null, null, null);
        director = createUser("director.del@dypiu.ac.in", "dir_del", UserRole.DIRECTOR, school.getId(), null, null);
        otherDirector = createUser("otherdir.del@dypiu.ac.in", "otherdir_del", UserRole.DIRECTOR, otherSchool.getId(), null, null);
        hod = createUser("hod.del@dypiu.ac.in", "hod_del", UserRole.HOD, school.getId(), department.getId(), null);
        otherHod = createUser("otherhod.del@dypiu.ac.in", "otherhod_del", UserRole.HOD, school.getId(), otherDept.getId(), null);
        pc = createUser("pc.del@dypiu.ac.in", "pc_del", UserRole.PROGRAMME_COORDINATOR, school.getId(), department.getId(), programme.getId());
        faculty = createUser("faculty.del@dypiu.ac.in", "fac_del", UserRole.FACULTY, school.getId(), department.getId(), programme.getId());

        iqacToken = generateToken(iqac);
        directorToken = generateToken(director);
        otherDirectorToken = generateToken(otherDirector);
        hodToken = generateToken(hod);
        otherHodToken = generateToken(otherHod);
        pcToken = generateToken(pc);
        facultyToken = generateToken(faculty);
    }

    private User createUser(String email, String username, UserRole role, String schoolId, String deptId, String progId) {
        return userRepository.save(User.builder()
                .email(email)
                .username(username)
                .name(username)
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .role(role)
                .schoolId(schoolId)
                .departmentId(deptId)
                .masterProgrammeId(progId)
                .isActive(true)
                .build());
    }

    private String generateToken(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        return jwtTokenProvider.generateToken(auth);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void testPcCanRequestProgrammeBatchCourseDeletion() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH_COURSE)
                .resourceId(batchCourse.getId())
                .remarks("Curriculum elective removed")
                .build();

        ResponseEntity<ApiResponse> res = restTemplate.exchange(
                "/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(pcToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());

        List<DeletionRequest> requests = deletionRequestRepository.findAll();
        assertEquals(1, requests.size());
        assertEquals(DeletionRequestStatus.PENDING, requests.get(0).getStatus());
        assertEquals(ResourceType.PROGRAMME_BATCH_COURSE, requests.get(0).getResourceType());
        assertEquals(batchCourse.getId(), requests.get(0).getResourceId());

        // Check AuditLog
        List<AuditLog> auditLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(batchCourse.getId());
        assertFalse(auditLogs.isEmpty());
        assertEquals(AuditAction.DELETE_REQUESTED, auditLogs.get(0).getAction());
        assertEquals(ResourceType.PROGRAMME_BATCH_COURSE, auditLogs.get(0).getResourceType());
    }

    @Test
    void testPcCannotRequestProgrammeBatchDeletion() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH)
                .resourceId(batch.getId())
                .remarks("Attempt to delete batch")
                .build();

        ResponseEntity<ApiResponse> res = restTemplate.exchange(
                "/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(pcToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    void testHodCanRequestProgrammeBatchDeletion() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH)
                .resourceId(batch.getId())
                .remarks("Zero admissions in batch")
                .build();

        ResponseEntity<ApiResponse> res = restTemplate.exchange(
                "/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(hodToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertTrue(res.getBody().isSuccess());

        List<DeletionRequest> requests = deletionRequestRepository.findAll();
        assertEquals(1, requests.size());
        assertEquals(DeletionRequestStatus.PENDING, requests.get(0).getStatus());
        assertEquals(ResourceType.PROGRAMME_BATCH, requests.get(0).getResourceType());

        // Check AuditLog
        List<AuditLog> auditLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(batch.getId());
        assertFalse(auditLogs.isEmpty());
        assertEquals(AuditAction.DELETE_REQUESTED, auditLogs.get(0).getAction());
        assertEquals(ResourceType.PROGRAMME_BATCH, auditLogs.get(0).getResourceType());
    }

    @Test
    void testIqacCannotRequestDeletion() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH_COURSE)
                .resourceId(batchCourse.getId())
                .remarks("IQAC deletion attempt")
                .build();

        // IQAC cannot request
        ResponseEntity<ApiResponse> iqacRes = restTemplate.exchange(
                "/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(iqacToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.FORBIDDEN, iqacRes.getStatusCode());
    }

    @Test
    void testHodCanRejectProgrammeBatchCourseDeletion() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH_COURSE)
                .resourceId(batchCourse.getId())
                .remarks("Request to delete")
                .build();
        restTemplate.exchange("/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(pcToken)), ApiResponse.class);
        DeletionRequest created = deletionRequestRepository.findAll().get(0);

        DeletionRejectDto rejectDto = DeletionRejectDto.builder().remarks("Course is mandatory for NBA accreditation").build();
        ResponseEntity<ApiResponse> rejectRes = restTemplate.exchange(
                "/deletion-requests/" + created.getId() + "/reject", HttpMethod.POST, new HttpEntity<>(rejectDto, authHeaders(hodToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.OK, rejectRes.getStatusCode());
        DeletionRequest updated = deletionRequestRepository.findById(created.getId()).orElseThrow();
        assertEquals(DeletionRequestStatus.REJECTED, updated.getStatus());
        assertEquals("Course is mandatory for NBA accreditation", updated.getRejectionReason());

        // Verify underlying resource is NOT soft-deleted
        ProgrammeBatchCourse checkedOffering = programmeBatchCourseRepository.findById(batchCourse.getId()).orElseThrow();
        assertNull(checkedOffering.getDeletedAt());

        // Check AuditLog
        List<AuditLog> auditLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(batchCourse.getId());
        assertEquals(AuditAction.DELETE_REJECTED, auditLogs.get(0).getAction());
    }

    @Test
    void testHodExecuteWithValidPasswordExecutesSoftDelete() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH_COURSE)
                .resourceId(batchCourse.getId())
                .remarks("Request to delete")
                .build();
        restTemplate.exchange("/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(pcToken)), ApiResponse.class);
        DeletionRequest created = deletionRequestRepository.findAll().get(0);

        DeletionExecuteDto execDto = DeletionExecuteDto.builder().password("SecretPass123!").build();
        ResponseEntity<ApiResponse> execRes = restTemplate.exchange(
                "/deletion-requests/" + created.getId() + "/execute", HttpMethod.POST, new HttpEntity<>(execDto, authHeaders(hodToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.OK, execRes.getStatusCode());

        DeletionRequest updatedReq = deletionRequestRepository.findById(created.getId()).orElseThrow();
        assertEquals(DeletionRequestStatus.EXECUTED, updatedReq.getStatus());
        assertNotNull(updatedReq.getExecutedAt());

        // Verify underlying resource is SOFT DELETED (Record remains in DB, deletedAt and deletedBy populated)
        // 1. Inactive in standard queries
        assertTrue(programmeBatchCourseRepository.findById(batchCourse.getId()).isEmpty());
        // 2. Retained in DB/Trash
        ProgrammeBatchCourse checkedOffering = programmeBatchCourseRepository.findTrashCourseById(batchCourse.getId()).orElseThrow();
        assertNotNull(checkedOffering.getDeletedAt());
        assertEquals(hod.getEmail(), checkedOffering.getDeletedBy());
        assertEquals("DELETED", checkedOffering.getStatus());

        // Verify Audit Logs
        List<AuditLog> auditLogs = auditLogRepository.findByResourceIdOrderByCreatedAtDesc(batchCourse.getId());
        assertEquals(AuditAction.DELETE_EXECUTED, auditLogs.get(0).getAction());
        assertEquals(AuditAction.DELETE_APPROVED, auditLogs.get(1).getAction());
    }

    @Test
    void testDirectorExecuteWithValidPasswordExecutesBatchSoftDelete() {
        DeletionRequestCreateDto req = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH)
                .resourceId(batch.getId())
                .remarks("Batch cancellation request")
                .build();
        restTemplate.exchange("/deletion-requests", HttpMethod.POST, new HttpEntity<>(req, authHeaders(hodToken)), ApiResponse.class);
        DeletionRequest created = deletionRequestRepository.findAll().get(0);

        DeletionExecuteDto execDto = DeletionExecuteDto.builder().password("SecretPass123!").build();
        ResponseEntity<ApiResponse> execRes = restTemplate.exchange(
                "/deletion-requests/" + created.getId() + "/execute", HttpMethod.POST, new HttpEntity<>(execDto, authHeaders(directorToken)), ApiResponse.class
        );

        assertEquals(HttpStatus.OK, execRes.getStatusCode());

        assertTrue(programmeBatchRepository.findById(batch.getId()).isEmpty());
        ProgrammeBatch checkedBatch = programmeBatchRepository.findTrashBatchById(batch.getId()).orElseThrow();
        assertNotNull(checkedBatch.getDeletedAt());
        assertEquals(director.getEmail(), checkedBatch.getDeletedBy());
        assertEquals("DELETED", checkedBatch.getStatus());
    }

    @Test
    void testIqacRetainsAuditLogReadAccess() {
        ResponseEntity<ApiResponse> iqacAudit = restTemplate.exchange(
                "/audit-logs", HttpMethod.GET, new HttpEntity<>(authHeaders(iqacToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.OK, iqacAudit.getStatusCode());
    }

    @Test
    void testCrossDepartmentAndCrossSchoolExecutionBlocked() {
        // Cross department HOD
        DeletionRequestCreateDto req1 = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH_COURSE)
                .resourceId(batchCourse.getId())
                .remarks("PC Request")
                .build();
        restTemplate.exchange("/deletion-requests", HttpMethod.POST, new HttpEntity<>(req1, authHeaders(pcToken)), ApiResponse.class);
        DeletionRequest created1 = deletionRequestRepository.findAll().get(0);

        DeletionExecuteDto execDto = DeletionExecuteDto.builder().password("SecretPass123!").build();
        ResponseEntity<ApiResponse> crossDeptRes = restTemplate.exchange(
                "/deletion-requests/" + created1.getId() + "/execute", HttpMethod.POST, new HttpEntity<>(execDto, authHeaders(otherHodToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.FORBIDDEN, crossDeptRes.getStatusCode());

        // Cross school Director
        DeletionRequestCreateDto req2 = DeletionRequestCreateDto.builder()
                .resourceType(ResourceType.PROGRAMME_BATCH)
                .resourceId(batch.getId())
                .remarks("HOD Request")
                .build();
        restTemplate.exchange("/deletion-requests", HttpMethod.POST, new HttpEntity<>(req2, authHeaders(hodToken)), ApiResponse.class);
        DeletionRequest created2 = deletionRequestRepository.findByResourceTypeAndResourceIdAndStatus(ResourceType.PROGRAMME_BATCH, batch.getId(), DeletionRequestStatus.PENDING).orElseThrow();

        ResponseEntity<ApiResponse> crossSchoolRes = restTemplate.exchange(
                "/deletion-requests/" + created2.getId() + "/execute", HttpMethod.POST, new HttpEntity<>(execDto, authHeaders(otherDirectorToken)), ApiResponse.class
        );
        assertEquals(HttpStatus.FORBIDDEN, crossSchoolRes.getStatusCode());
    }
}
