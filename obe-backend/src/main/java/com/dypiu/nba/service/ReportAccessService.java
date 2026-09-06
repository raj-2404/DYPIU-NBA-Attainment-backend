package com.dypiu.nba.service;

import com.dypiu.nba.dto.ReportFiltersDto;
import com.dypiu.nba.entity.*;
import com.dypiu.nba.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAccessService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final MasterCourseRepository masterCourseRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final ApprovalRequestRepository approvalRequestRepository;

    private static final java.util.Comparator<ApprovalRequest> LATEST_APPROVAL_COMPARATOR = (a, b) -> {
        ZonedDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : (a.getApprovedAt() != null ? a.getApprovedAt() : (a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt()));
        ZonedDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : (b.getApprovedAt() != null ? b.getApprovedAt() : (b.getSubmittedAt() != null ? b.getSubmittedAt() : b.getCreatedAt()));
        if (ta != null && tb != null) {
            int cmp = ta.compareTo(tb);
            if (cmp != 0) return cmp;
        }
        if (ta == null && tb != null) return -1;
        if (ta != null && tb == null) return 1;
        if (a.getId() != null && b.getId() != null) {
            return a.getId().compareTo(b.getId());
        }
        return 0;
    };

    public boolean isCourseAllocationApproved(ProgrammeBatchCourse offering) {
        if (offering == null) return false;
        String progId = null;
        if (offering.getMasterCourseId() != null) {
            MasterCourse c = masterCourseRepository.findById(offering.getMasterCourseId()).orElse(null);
            if (c != null && c.getMasterProgrammeId() != null) {
                progId = c.getMasterProgrammeId();
            }
        }
        if (progId == null && offering.getProgrammeBatchId() != null) {
            ProgrammeBatch b = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
            if (b != null && b.getMasterProgrammeId() != null) {
                progId = b.getMasterProgrammeId();
            }
        }
        if (progId == null || progId.isBlank()) return false;
        final String targetProgId = progId.replace("allocation-", "").replace("allocation_", "").replace("allocation", "").trim();
        return approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.COURSE_ALLOCATION || a.getType() == ApprovalType.COURSE_OFFERING)
                        && (targetProgId.equalsIgnoreCase(a.getMasterProgrammeId())
                        || ("allocation-" + targetProgId).equalsIgnoreCase(a.getResourceId())
                        || targetProgId.equalsIgnoreCase(a.getResourceId())
                        || (a.getResourceId() != null && a.getResourceId().toLowerCase().contains(targetProgId.toLowerCase()))))
                .max(LATEST_APPROVAL_COMPARATOR)
                .map(a -> a.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
    }

    public boolean isCourseCoordinatorAssigned(ProgrammeBatchCourse offering, User user) {
        if (offering == null || user == null) return false;
        return isCourseCoordinatorAssigned(offering, user.getId(), user.getUsername(), user.getName(), user.getEmail());
    }

    public boolean isCourseCoordinatorAssigned(ProgrammeBatchCourse offering, Long userId, String username, String userName, String userEmail) {
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

        // 3. programmeBatchCourse.courseCoordinatorName equals authenticatedUser.name or username, case-insensitive
        String ccName = offering.getCourseCoordinatorName();
        boolean nameMatch = ccName != null && !ccName.isBlank() && userName != null && !userName.isBlank()
                && ccName.trim().equalsIgnoreCase(userName.trim());
        boolean usernameMatch = ccName != null && !ccName.isBlank() && username != null && !username.isBlank()
                && ccName.trim().equalsIgnoreCase(username.trim());

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
                } else if (username != null && !username.isBlank() && assignedFaculty.toLowerCase().contains(username.trim().toLowerCase())) {
                    assignedFacultyFallback = true;
                }
            }
        }

        return idMatch || ccEmailMatch || cEmailMatch || nameMatch || usernameMatch || nameEmailMatch || assignedFacultyFallback;
    }

    @Transactional(readOnly = true)
    public User getAuthenticatedUser(Principal principal) {
        System.out.println("[ReportAccessService] getAuthenticatedUser called | principal: " + (principal != null ? principal.getName() : "null"));
        String usernameOrEmail = null;
        if (principal != null) {
            usernameOrEmail = principal.getName();
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                usernameOrEmail = auth.getName();
            }
        }

        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return userRepository.findAll().stream().findFirst().orElse(null);
        }

        final String lookup = usernameOrEmail;
        return userRepository.findByUsername(lookup)
                .or(() -> userRepository.findByEmail(lookup))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public void validateProgrammeAccess(User user, String masterProgrammeId) {
        System.out.println("[ReportAccessService] validateProgrammeAccess called | user: " + (user != null ? user.getEmail() : "null") + " | masterProgrammeId: " + masterProgrammeId);
        if (user == null || masterProgrammeId == null) return;
        if (user.getRole() == UserRole.IQAC) return;

        MasterProgramme prog = masterProgrammeRepository.findById(masterProgrammeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme not found: " + masterProgrammeId));

        if (user.getRole() == UserRole.PROGRAMME_COORDINATOR) {
            boolean matchesDirect = user.getMasterProgrammeId() != null && user.getMasterProgrammeId().equalsIgnoreCase(masterProgrammeId);
            boolean matchesBatch = false;
            if (!matchesDirect && user.getEmail() != null && !user.getEmail().isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(user.getEmail().trim());
                if (batches != null) {
                    matchesBatch = batches.stream().anyMatch(b -> masterProgrammeId.equalsIgnoreCase(b.getMasterProgrammeId()));
                }
            }
            if (!matchesDirect && !matchesBatch) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Programme is outside your assigned programme scope.");
            }
            return;
        }

        Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
        if (user.getRole() == UserRole.HOD) {
            boolean hodMatch = false;
            if (user.getDepartmentId() != null && dept != null && user.getDepartmentId().equalsIgnoreCase(dept.getId())) {
                hodMatch = true;
            } else if (user.getEmail() != null && !user.getEmail().isBlank() && dept != null) {
                List<Department> hodDepts = departmentRepository.findByHodEmailIgnoreCase(user.getEmail().trim());
                if (hodDepts != null && !hodDepts.isEmpty()) {
                    hodMatch = hodDepts.stream().anyMatch(d -> dept.getId().equalsIgnoreCase(d.getId()));
                }
            }
            if (!hodMatch) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Programme is outside your department scope.");
            }
            return;
        }

        if (user.getRole() == UserRole.DIRECTOR) {
            if (user.getSchoolId() != null && dept != null && !user.getSchoolId().equals(dept.getSchoolId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Programme is outside your school scope.");
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateBatchAccess(User user, String programmeBatchId) {
        System.out.println("[ReportAccessService] validateBatchAccess called | user: " + (user != null ? user.getEmail() : "null") + " | programmeBatchId: " + programmeBatchId);
        if (user == null || programmeBatchId == null) return;
        if (user.getRole() == UserRole.IQAC) return;

        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found: " + programmeBatchId));

        if (user.getRole() == UserRole.PROGRAMME_COORDINATOR) {
            boolean isAssigned = (user.getId() != null && Objects.equals(batch.getCoordinatorId(), user.getId()))
                    || (user.getEmail() != null && batch.getCoordinatorEmail() != null && batch.getCoordinatorEmail().trim().equalsIgnoreCase(user.getEmail().trim()))
                    || (user.getName() != null && batch.getCoordinatorName() != null && batch.getCoordinatorName().trim().equalsIgnoreCase(user.getName().trim()))
                    || (user.getMasterProgrammeId() != null && user.getMasterProgrammeId().equalsIgnoreCase(batch.getMasterProgrammeId()));
            if (!isAssigned && user.getEmail() != null && !user.getEmail().isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(user.getEmail().trim());
                if (batches != null) {
                    isAssigned = batches.stream().anyMatch(b -> b.getId().equals(batch.getId()) || (batch.getMasterProgrammeId() != null && batch.getMasterProgrammeId().equalsIgnoreCase(b.getMasterProgrammeId())));
                }
            }
            if (!isAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not the assigned Programme Coordinator for this Programme Batch.");
            }
            return;
        }

        validateProgrammeAccess(user, batch.getMasterProgrammeId());
    }

    @Transactional(readOnly = true)
    public void validateCourseOfferingAccess(User user, String programmeBatchCourseId) {
        System.out.println("[ReportAccessService] validateCourseOfferingAccess called | user: " + (user != null ? user.getEmail() : "null") + " | programmeBatchCourseId: " + programmeBatchCourseId);
        if (user == null || programmeBatchCourseId == null) return;
        if (user.getRole() == UserRole.IQAC) return;

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(programmeBatchCourseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + programmeBatchCourseId));

        if (user.getRole() == UserRole.FACULTY) {
            boolean idMatch = offering.getCourseCoordinatorId() != null && user.getId() != null
                    && Objects.equals(offering.getCourseCoordinatorId(), user.getId());

            String ccEmail = offering.getCourseCoordinatorEmail();
            boolean ccEmailMatch = ccEmail != null && !ccEmail.isBlank() && user.getEmail() != null && !user.getEmail().isBlank()
                    && ccEmail.trim().equalsIgnoreCase(user.getEmail().trim());

            String cEmail = offering.getCoordinatorEmail();
            boolean cEmailMatch = cEmail != null && !cEmail.isBlank() && user.getEmail() != null && !user.getEmail().isBlank()
                    && cEmail.trim().equalsIgnoreCase(user.getEmail().trim());

            String ccName = offering.getCourseCoordinatorName();
            boolean nameMatch = ccName != null && !ccName.isBlank() && user.getName() != null && !user.getName().isBlank()
                    && ccName.trim().equalsIgnoreCase(user.getName().trim());
            boolean usernameMatch = ccName != null && !ccName.isBlank() && user.getUsername() != null && !user.getUsername().isBlank()
                    && ccName.trim().equalsIgnoreCase(user.getUsername().trim());

            boolean nameEmailMatch = ccName != null && !ccName.isBlank() && user.getEmail() != null && !user.getEmail().isBlank()
                    && ccName.trim().equalsIgnoreCase(user.getEmail().trim());

            boolean assignedFacultyFallback = false;
            if (offering.getCourseCoordinatorId() == null && (ccName == null || ccName.isBlank())) {
                String assignedFaculty = offering.getAssignedFaculty();
                if (assignedFaculty != null && !assignedFaculty.isBlank()) {
                    if (user.getEmail() != null && !user.getEmail().isBlank() && assignedFaculty.toLowerCase().contains(user.getEmail().trim().toLowerCase())) {
                        assignedFacultyFallback = true;
                    } else if (user.getName() != null && !user.getName().isBlank() && assignedFaculty.toLowerCase().contains(user.getName().trim().toLowerCase())) {
                        assignedFacultyFallback = true;
                    } else if (user.getUsername() != null && !user.getUsername().isBlank() && assignedFaculty.toLowerCase().contains(user.getUsername().trim().toLowerCase())) {
                        assignedFacultyFallback = true;
                    }
                }
            }

            boolean isCoordinator = idMatch || ccEmailMatch || cEmailMatch || nameMatch || usernameMatch || nameEmailMatch || assignedFacultyFallback;

            if (!isCoordinator) {
                log.info("Course coordinator authorization failed for resolved ProgrammeBatchCourse ID={}: authenticated JWT user [id={}, username={}, name={}, email={}, role={}], offering coordinator [courseCoordinatorId={}, courseCoordinatorName={}, courseCoordinatorEmail={}, coordinatorEmail={}, assignedFaculty={}], comparison results [idMatch={}, ccEmailMatch={}, cEmailMatch={}, nameMatch={}, usernameMatch={}, nameEmailMatch={}, assignedFacultyFallback={}]",
                        offering.getId(), user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.getRole(),
                        offering.getCourseCoordinatorId(), offering.getCourseCoordinatorName(), offering.getCourseCoordinatorEmail(), offering.getCoordinatorEmail(), offering.getAssignedFaculty(),
                        idMatch, ccEmailMatch, cEmailMatch, nameMatch, usernameMatch, nameEmailMatch, assignedFacultyFallback);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this Course Offering.");
            }
            if (!isCourseAllocationApproved(offering)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
            }
            return;
        }

        validateBatchAccess(user, offering.getProgrammeBatchId());
    }

    @Transactional(readOnly = true)
    public void validateCourseCoordinatorAccess(User user, String programmeBatchCourseId) {
        System.out.println("[ReportAccessService] validateCourseCoordinatorAccess called | user: " + (user != null ? user.getEmail() : "null") + " | programmeBatchCourseId: " + programmeBatchCourseId);
        if (user == null || programmeBatchCourseId == null) return;
        if (user.getRole() == UserRole.IQAC || user.getRole() == UserRole.DIRECTOR || user.getRole() == UserRole.HOD || user.getRole() == UserRole.PROGRAMME_COORDINATOR) {
            validateCourseOfferingAccess(user, programmeBatchCourseId);
            return;
        }

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(programmeBatchCourseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course Offering not found: " + programmeBatchCourseId));

        boolean isCoordinator = isCourseCoordinatorAssigned(offering, user);
        if (!isCoordinator) {
            log.info("Course coordinator authorization failed for resolved ProgrammeBatchCourse ID={}: authenticated JWT user [id={}, username={}, name={}, email={}, role={}], offering coordinator [courseCoordinatorId={}, courseCoordinatorName={}, courseCoordinatorEmail={}, coordinatorEmail={}, assignedFaculty={}]",
                    offering.getId(), user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.getRole(),
                    offering.getCourseCoordinatorId(), offering.getCourseCoordinatorName(), offering.getCourseCoordinatorEmail(), offering.getCoordinatorEmail(), offering.getAssignedFaculty());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only the assigned Course Coordinator can perform this action.");
        }
        if (!isCourseAllocationApproved(offering)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
        }
    }

    @Transactional(readOnly = true)
    public void validateCourseAccess(User user, String masterCourseId) {
        System.out.println("[ReportAccessService] validateCourseAccess called | user: " + (user != null ? user.getEmail() : "null") + " | masterCourseId: " + masterCourseId);
        if (user == null || masterCourseId == null) return;
        if (user.getRole() == UserRole.IQAC) return;

        MasterCourse course = masterCourseRepository.findById(masterCourseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found: " + masterCourseId));

        if (user.getRole() == UserRole.FACULTY) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
            boolean hasAssignedOffering = offerings.stream().anyMatch(o -> isCourseCoordinatorAssigned(o, user));
            if (!hasAssignedOffering) {
                log.info("Course coordinator authorization failed for course {}: authenticated user [id={}, name={}, email={}]",
                        course.getId(), user.getId(), user.getName(), user.getEmail());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to any offering of this Course.");
            }
            boolean hasApprovedOffering = offerings.stream().anyMatch(o -> isCourseCoordinatorAssigned(o, user) && isCourseAllocationApproved(o));
            if (!hasApprovedOffering) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
            }
            return;
        }

        validateProgrammeAccess(user, course.getMasterProgrammeId());
    }

    @Transactional(readOnly = true)
    public void validateCourseAtrAccess(User user, String programmeBatchCourseId) {
        System.out.println("[ReportAccessService] validateCourseAtrAccess called | user: " + (user != null ? user.getEmail() : "null") + " | programmeBatchCourseId: " + programmeBatchCourseId);
        validateCourseOfferingAccess(user, programmeBatchCourseId);
    }

    @Transactional(readOnly = true)
    public void validateProgrammeAtrAccess(User user, String masterProgrammeId, String programmeBatchId) {
        System.out.println("[ReportAccessService] validateProgrammeAtrAccess called | user: " + (user != null ? user.getEmail() : "null") + " | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        if (user == null) return;
        if (user.getRole() == UserRole.FACULTY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Course Coordinators / Faculty do not have permission to view or edit Programme ATRs.");
        }
        if (masterProgrammeId != null) {
            validateProgrammeAccess(user, masterProgrammeId);
        }
        if (programmeBatchId != null) {
            validateBatchAccess(user, programmeBatchId);
        }
    }

    @Transactional(readOnly = true)
    public ReportFiltersDto getReportFilters(User user) {
        System.out.println("[ReportAccessService] getReportFilters called | user: " + (user != null ? user.getEmail() : "null"));
        if (user == null) {
            return ReportFiltersDto.builder()
                    .role("GUEST")
                    .programmes(Collections.emptyList())
                    .batches(Collections.emptyList())
                    .courseOfferings(Collections.emptyList())
                    .build();
        }

        String roleStr = user.getRole().name();
        List<MasterProgramme> allowedProgrammes = new ArrayList<>();
        List<ProgrammeBatch> allowedBatches = new ArrayList<>();
        List<ProgrammeBatchCourse> allowedOfferings = new ArrayList<>();

        if (user.getRole() == UserRole.IQAC) {
            allowedProgrammes = masterProgrammeRepository.findAll();
            allowedBatches = programmeBatchRepository.findAll();
            allowedOfferings = programmeBatchCourseRepository.findAll();
        } else if (user.getRole() == UserRole.DIRECTOR) {
            String schoolId = user.getSchoolId();
            List<Department> depts = schoolId != null ? departmentRepository.findBySchoolId(schoolId) : departmentRepository.findAll();
            Set<String> deptIds = depts.stream().map(Department::getId).collect(Collectors.toSet());
            allowedProgrammes = masterProgrammeRepository.findAll().stream().filter(p -> deptIds.contains(p.getDepartmentId())).collect(Collectors.toList());
            Set<String> progIds = allowedProgrammes.stream().map(MasterProgramme::getId).collect(Collectors.toSet());
            allowedBatches = programmeBatchRepository.findAll().stream().filter(b -> progIds.contains(b.getMasterProgrammeId())).collect(Collectors.toList());
            Set<String> programmeBatchIds = allowedBatches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            allowedOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdIn(programmeBatchIds);
        } else if (user.getRole() == UserRole.HOD) {
            String deptId = user.getDepartmentId();
            allowedProgrammes = deptId != null ? masterProgrammeRepository.findByDepartmentId(deptId) : masterProgrammeRepository.findAll();
            Set<String> progIds = allowedProgrammes.stream().map(MasterProgramme::getId).collect(Collectors.toSet());
            allowedBatches = programmeBatchRepository.findAll().stream().filter(b -> progIds.contains(b.getMasterProgrammeId())).collect(Collectors.toList());
            Set<String> programmeBatchIds = allowedBatches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            allowedOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdIn(programmeBatchIds);
        } else if (user.getRole() == UserRole.PROGRAMME_COORDINATOR) {
            String progId = user.getMasterProgrammeId();
            if (progId != null && !progId.isBlank()) {
                allowedProgrammes = masterProgrammeRepository.findById(progId).map(List::of).orElse(Collections.emptyList());
                allowedBatches = programmeBatchRepository.findByMasterProgrammeId(progId);
            } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
                allowedBatches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(user.getEmail().trim());
                Set<String> progIds = allowedBatches.stream().map(ProgrammeBatch::getMasterProgrammeId).filter(Objects::nonNull).collect(Collectors.toSet());
                allowedProgrammes = masterProgrammeRepository.findAllById(progIds);
            }
            Set<String> programmeBatchIds = allowedBatches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            allowedOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdIn(programmeBatchIds);
        } else if (user.getRole() == UserRole.FACULTY) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findAll().stream()
                    .filter(o -> o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), user.getId()))
                    .collect(Collectors.toList());
            allowedOfferings = offerings;
            Set<String> programmeBatchIds = offerings.stream().map(ProgrammeBatchCourse::getProgrammeBatchId).collect(Collectors.toSet());
            allowedBatches = programmeBatchRepository.findAll().stream().filter(b -> programmeBatchIds.contains(b.getId())).collect(Collectors.toList());
            Set<String> progIds = allowedBatches.stream().map(ProgrammeBatch::getMasterProgrammeId).collect(Collectors.toSet());
            allowedProgrammes = masterProgrammeRepository.findAll().stream().filter(p -> progIds.contains(p.getId())).collect(Collectors.toList());
        }

        Map<String, MasterCourse> courseMap = masterCourseRepository.findAll().stream().collect(Collectors.toMap(MasterCourse::getId, c -> c, (a, b) -> a));

        List<ReportFiltersDto.Item> progItems = allowedProgrammes.stream()
                .map(p -> ReportFiltersDto.Item.builder().id(p.getId()).name(p.getName()).code(p.getCode()).build())
                .collect(Collectors.toList());

        List<ReportFiltersDto.BatchItem> batchItems = allowedBatches.stream()
                .map(b -> ReportFiltersDto.BatchItem.builder().id(b.getId()).masterProgrammeId(b.getMasterProgrammeId()).name(b.getName()).build())
                .collect(Collectors.toList());

        List<ReportFiltersDto.OfferingItem> offeringItems = allowedOfferings.stream()
                .map(o -> {
                    MasterCourse c = courseMap.get(o.getMasterCourseId());
                    return ReportFiltersDto.OfferingItem.builder()
                            .id(o.getId())
                            .masterCourseId(o.getMasterCourseId())
                            .programmeBatchId(o.getProgrammeBatchId())
                            .courseCode(c != null ? c.getCode() : "N/A")
                            .courseName(c != null ? c.getName() : "N/A")
                            .semester(o.getSemester())
                            .build();
                })
                .collect(Collectors.toList());

        return ReportFiltersDto.builder()
                .role(roleStr)
                .programmes(progItems)
                .batches(batchItems)
                .courseOfferings(offeringItems)
                .build();
    }
}
