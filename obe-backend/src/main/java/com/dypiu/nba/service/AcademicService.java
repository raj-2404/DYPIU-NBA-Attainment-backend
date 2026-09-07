package com.dypiu.nba.service;

import com.dypiu.nba.entity.*;
import com.dypiu.nba.exception.ResourceNotFoundException;
import com.dypiu.nba.repository.*;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dypiu.nba.dto.DirectorSetupProgressDto;
import com.dypiu.nba.dto.DirectorSchoolSummaryDto;
import com.dypiu.nba.dto.DepartmentSummaryDto;
import com.dypiu.nba.dto.HodDepartmentSummaryDto;
import com.dypiu.nba.dto.HodSetupProgressDto;
import com.dypiu.nba.dto.ProgrammeCoordinatorSummaryDto;
import com.dypiu.nba.dto.ProgrammeCoordinatorSetupProgressDto;
import com.dypiu.nba.dto.CourseCoordinatorSummaryDto;
import com.dypiu.nba.dto.CourseCoordinatorSetupProgressDto;
import com.dypiu.nba.dto.UserDto;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicService {

    private final CurrentUserScopeService currentUserScopeService;
    private final AuditLogService auditLogService;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final StudentRepository studentRepository;
    private final DirectorSetupProgressRepository directorSetupProgressRepository;
    private final HodSetupProgressRepository hodSetupProgressRepository;
    private final ProgrammeCoordinatorSetupProgressRepository pcSetupProgressRepository;
    private final CourseCoordinatorSetupProgressRepository ccSetupProgressRepository;
    private final AttainmentConfigurationRepository configRepository;
    private final BatchLifecycleService batchLifecycleService;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final ProgrammeOutcomeRepository programmeOutcomeRepository;
    private final ProgrammeSpecificOutcomeRepository programmeSpecificOutcomeRepository;
    private final PoCompetencyRepository poCompetencyRepository;
    private final PsoCompetencyRepository psoCompetencyRepository;
    private final PeoOutcomeRepository peoOutcomeRepository;
    private final UserRepository userRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;
    private final CourseAtrRepository courseAtrRepository;
    private final ProgrammeAtrRepository programmeAtrRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final com.dypiu.nba.security.RequestScopeAuthorizer requestScopeAuthorizer;

    private static final Comparator<String> NATURAL_CODE_COMPARATOR = (c1, c2) -> {
        if (c1 == null) return -1;
        if (c2 == null) return 1;
        String p1 = c1.replaceAll("\\D+", "");
        String p2 = c2.replaceAll("\\D+", "");
        if (!p1.isEmpty() && !p2.isEmpty()) {
            try {
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) return Integer.compare(n1, n2);
            } catch (NumberFormatException ignored) {}
        }
        return c1.compareToIgnoreCase(c2);
    };

    private CurrentUserScope getScope() {
        try {
            return currentUserScopeService.getCurrentUserScope();
        } catch (Exception e) {
            return null;
        }
    }

    private void enforceSchoolScope(String schoolId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (scope.isDirector() || scope.isHod() || scope.isProgrammeCoordinator()) {
            String requiredSchoolId = scope.getRequiredSchoolId();
            if (schoolId != null && !schoolId.equals(requiredSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned school scope.");
            }
        }
    }

    private void enforceDepartmentScope(String departmentId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (scope.isHod()) {
            if (departmentId != null && scope.getEmail() != null && !scope.getEmail().isBlank()) {
                List<Department> hodDepts = departmentRepository.findByHodEmailIgnoreCase(scope.getEmail().trim());
                if (hodDepts != null && !hodDepts.isEmpty()) {
                    boolean match = hodDepts.stream().anyMatch(d -> departmentId.equalsIgnoreCase(d.getId()));
                    if (!match) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned department scope.");
                    }
                    return;
                }
            }
            String requiredDeptId = scope.getRequiredDepartmentId();
            if (departmentId != null && !departmentId.equals(requiredDeptId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned department scope.");
            }
        }
        if (scope.isProgrammeCoordinator()) {
            if (scope.hasDepartmentScope()) {
                String requiredDeptId = scope.getDepartmentId();
                if (departmentId != null && !departmentId.equals(requiredDeptId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned department scope.");
                }
            }
            return;
        }
        if (scope.isDirector()) {
            if (departmentId != null) {
                Department dept = departmentRepository.findById(departmentId).orElse(null);
                if (dept != null && dept.getSchoolId() != null && !dept.getSchoolId().equals(scope.getRequiredSchoolId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Department is outside your assigned school scope.");
                }
            }
        }
    }

    private void enforceProgrammeScope(String masterProgrammeId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (masterProgrammeId == null || masterProgrammeId.isBlank()) return;

        if (scope.isProgrammeCoordinator()) {
            String requiredProgId = scope.getMasterProgrammeId();
            boolean matchesDirectProg = (requiredProgId != null && masterProgrammeId.equals(requiredProgId));
            boolean matchesBatchProg = false;
            if (!matchesDirectProg && scope.getEmail() != null && !scope.getEmail().isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(scope.getEmail().trim());
                matchesBatchProg = batches.stream().anyMatch(b -> masterProgrammeId.equals(b.getMasterProgrammeId()));
            }
            if (!matchesDirectProg && !matchesBatchProg) {
                MasterProgramme p = masterProgrammeRepository.findByIdAndDeletedAtIsNull(masterProgrammeId).orElse(null);
                if (p != null) {
                    if (scope.getEmail() != null && p.getCoordinatorEmail() != null && p.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim())) {
                        matchesDirectProg = true;
                    }
                    if (scope.getName() != null && p.getCoordinator() != null && p.getCoordinator().trim().equalsIgnoreCase(scope.getName().trim())) {
                        matchesDirectProg = true;
                    }
                }
            }
            if (!matchesDirectProg && !matchesBatchProg) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Resource is outside your assigned programme scope.");
            }
        }

        MasterProgramme prog = masterProgrammeRepository.findByIdAndDeletedAtIsNull(masterProgrammeId)
                .orElseThrow(() -> new ResourceNotFoundException("MasterProgramme not found: " + masterProgrammeId));
        if (prog.getDepartmentId() != null) {
            enforceDepartmentScope(prog.getDepartmentId());
            Department dept = departmentRepository.findById(prog.getDepartmentId()).orElse(null);
            if (dept != null && dept.getSchoolId() != null) {
                enforceSchoolScope(dept.getSchoolId());
            }
        }
    }

    private void enforceBatchScope(String programmeBatchId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (programmeBatchId == null || programmeBatchId.isBlank()) return;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("ProgrammeBatch not found: " + programmeBatchId));

        if (scope.isFaculty()) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
            boolean hasAssigned = offerings.stream().anyMatch(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()))
                        ;
                return isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
            });
            if (!hasAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to any MasterCourse Offering in this ProgrammeBatch.");
            }
            return;
        }

        enforceProgrammeScope(batch.getMasterProgrammeId());
    }

    private void enforceCourseScope(String courseOfferingOrId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (courseOfferingOrId == null || courseOfferingOrId.isBlank()) return;

        ProgrammeBatchCourse pbc = programmeBatchCourseRepository.findById(courseOfferingOrId.trim()).orElse(null);
        if (pbc != null) {
            enforceProgrammeBatchCourseScope(pbc.getId());
            return;
        }
    }

    public boolean isCourseAllocationApproved(ProgrammeBatchCourse offering) {
        if (offering == null) return false;
        if (offering.getProgrammeBatchId() != null && offering.getSemester() != null) {
            String semKey = "allocation-" + offering.getProgrammeBatchId().trim() + "-sem-" + offering.getSemester();
            if (isAllocationApproved(semKey)) {
                return true;
            }
        }
        String progId = null;
        if (offering.getProgrammeBatchId() != null) {
            ProgrammeBatch b = programmeBatchRepository.findById(offering.getProgrammeBatchId()).orElse(null);
            if (b != null && b.getMasterProgrammeId() != null) {
                progId = b.getMasterProgrammeId();
            }
        }
        return isAllocationApproved(progId);
    }

    private void enforceProgrammeBatchCourseScope(String offeringId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) return;
        if (offeringId == null || offeringId.isBlank()) return;

        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("MasterCourse offering not found: " + offeringId));

        if (scope.isFaculty()) {
            boolean isCoordinator = (offering.getCourseCoordinatorId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()))
                    ;
            boolean isFacultyAssigned = isCoordinator || (offering.getAssignedFaculty() != null && (offering.getAssignedFaculty().contains(scope.getEmail()) || offering.getAssignedFaculty().contains(scope.getName())));
            if (!isFacultyAssigned) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not assigned to this MasterCourse Offering.");
            }
            if (!isCourseAllocationApproved(offering)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
            }
            return;
        }

        if (offering.getMasterCourseId() != null) enforceCourseScope(offering.getMasterCourseId());
        if (offering.getProgrammeBatchId() != null) enforceBatchScope(offering.getProgrammeBatchId());
    }

    private void enforceCourseCoordinatorScope(String offeringOrMasterCourseId) {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac() || scope.isDirector() || scope.isHod() || scope.isProgrammeCoordinator()) {
            return;
        }
        if (offeringOrMasterCourseId == null || offeringOrMasterCourseId.isBlank()) return;
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringOrMasterCourseId).orElse(null);
        if (offering == null) {
            List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(offeringOrMasterCourseId);
            offering = offerings.stream().findFirst().orElse(null);
        }
        if (offering == null) {
            throw new ResourceNotFoundException("MasterCourse offering not found: " + offeringOrMasterCourseId);
        }
        boolean isCoordinator = (offering.getCourseCoordinatorId() != null && Objects.equals(offering.getCourseCoordinatorId(), scope.getUserId()))
                ;
        if (!isCoordinator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only the assigned MasterCourse Coordinator can perform this action.");
        }
        if (!isCourseAllocationApproved(offering)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Course allocation for this course has not been approved by the HOD yet.");
        }
    }

    @Transactional(readOnly = true)
    public com.dypiu.nba.dto.BatchContextDto getBatchContext(String programmeBatchId) {
        System.out.println("[AcademicService] getBatchContext called | programmeBatchId: " + programmeBatchId);
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResourceNotFoundException("ProgrammeBatch not found: " + programmeBatchId));

        enforceProgrammeScope(batch.getMasterProgrammeId());

        MasterProgramme prog = masterProgrammeRepository.findByIdAndDeletedAtIsNull(batch.getMasterProgrammeId()).orElse(null);
        Department dept = (prog != null && prog.getDepartmentId() != null) ? departmentRepository.findById(prog.getDepartmentId()).orElse(null) : null;
        School school = (dept != null && dept.getSchoolId() != null) ? schoolRepository.findById(dept.getSchoolId()).orElse(null) : null;

        List<Student> students = studentRepository.findByProgrammeBatchId(programmeBatchId);
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
        Set<String> uniqueCourseCodes = offerings.stream().map(ProgrammeBatchCourse::getEffectiveCourseCode).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> offeringIds = offerings.stream().map(ProgrammeBatchCourse::getId).collect(Collectors.toList());

        long completedAtrs = offeringIds.isEmpty() ? 0 : courseAtrRepository.findByProgrammeBatchCourseIdIn(offeringIds).stream()
                                                         .filter(a -> a.getStatus() == CourseAtrStatus.VERIFIED)
                                                         .count();

        String progAtrStatus = "DRAFT";
        if (prog != null) {
            Optional<ProgrammeAtr> patr = programmeAtrRepository.findByProgrammeBatchId(programmeBatchId);
            if (patr.isPresent() && patr.get().getStatus() != null) {
                progAtrStatus = patr.get().getStatus().name();
            }
        }

        return com.dypiu.nba.dto.BatchContextDto.builder()
                .batch(com.dypiu.nba.dto.BatchContextDto.BatchSummary.builder()
                        .id(batch.getId())
                        .name(batch.getName())
                        .masterProgrammeId(batch.getMasterProgrammeId())
                        .programmeName(batch.getProgrammeName())
                        .status(batch.getStatus())
                        .build())
                .programme(prog != null ? com.dypiu.nba.dto.BatchContextDto.ProgrammeSummary.builder()
                                          .id(prog.getId())
                                          .code(prog.getCode())
                                          .name(prog.getName())
                                          .build() : null)
                .department(dept != null ? com.dypiu.nba.dto.BatchContextDto.DepartmentSummary.builder()
                                           .id(dept.getId())
                                           .name(dept.getName())
                                           .build() : null)
                .school(school != null ? com.dypiu.nba.dto.BatchContextDto.SchoolSummary.builder()
                                         .id(school.getId())
                                         .name(school.getName())
                                         .build() : null)
                .statistics(com.dypiu.nba.dto.BatchContextDto.Statistics.builder()
                        .studentCount(students.size())
                        .courseCount(uniqueCourseCodes.isEmpty() ? offerings.size() : uniqueCourseCodes.size())
                        .courseOfferingCount(offerings.size())
                        .completedCourseAtrCount(completedAtrs)
                        .programmeAtrStatus(progAtrStatus)
                        .build())
                .build();
    }

    private String cleanOverride(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatAssignedFaculty(Object assignedFaculty) {
        if (assignedFaculty == null) return null;
        if (assignedFaculty instanceof java.util.Collection<?> col) {
            if (col.isEmpty()) return null;
            return col.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
        }
        String s = assignedFaculty.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public ProgrammeBatchCourse enrichOffering(ProgrammeBatchCourse offering) {
        if (offering == null) return null;

        String codeOverride = cleanOverride(offering.getCourseCodeOverride());
        String nameOverride = cleanOverride(offering.getCourseNameOverride());
        offering.setCourseCodeOverride(codeOverride);
        offering.setCourseNameOverride(nameOverride);

        if (offering.getCode() != null && !offering.getCode().isBlank()) {
            offering.setCourseCode(offering.getCode());
        } else if (codeOverride != null) {
            offering.setCourseCode(codeOverride);
        }

        if (offering.getName() != null && !offering.getName().isBlank()) {
            offering.setCourseName(offering.getName());
        } else if (nameOverride != null) {
            offering.setCourseName(nameOverride);
        }

        if (offering.getProgrammeBatchId() != null) {
            programmeBatchRepository.findById(offering.getProgrammeBatchId()).ifPresent(batch -> {
                if (offering.getMasterProgrammeId() == null || offering.getMasterProgrammeId().isBlank()) {
                    offering.setMasterProgrammeId(batch.getMasterProgrammeId());
                }
                if (offering.getAcademicYear() == null || offering.getAcademicYear().isBlank()) {
                    String ay = (batch.getStartYear() != null && batch.getEndYear() != null)
                            ? (batch.getStartYear() + "-" + (batch.getEndYear() % 100))
                            : null;
                    offering.setAcademicYear(ay);
                }
            });
        }

        if (offering.getCourseCoordinatorId() != null) {
            userRepository.findById(offering.getCourseCoordinatorId()).ifPresent(user -> {
                if (offering.getCourseCoordinatorName() == null || offering.getCourseCoordinatorName().isBlank()) {
                    offering.setCourseCoordinatorName(user.getName());
                }
                offering.setCoordinatorEmail(user.getEmail() != null ? user.getEmail() : user.getUsername());
            });
        } else if (offering.getAssignedFaculty() != null && !offering.getAssignedFaculty().isBlank()) {
            String firstFaculty = offering.getAssignedFaculty().split("[,;]")[0].trim();
            if (firstFaculty.contains("@")) {
                userRepository.findByEmailIgnoreCase(firstFaculty)
                        .or(() -> userRepository.findByUsernameIgnoreCase(firstFaculty))
                        .ifPresent(user -> {
                            offering.setCourseCoordinatorId(user.getId());
                            if (offering.getCourseCoordinatorName() == null || offering.getCourseCoordinatorName().isBlank()) {
                                offering.setCourseCoordinatorName(user.getName());
                            }
                            offering.setCoordinatorEmail(user.getEmail() != null ? user.getEmail() : user.getUsername());
                        });
            }
        }

        return offering;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatchCourse> getProgrammeBatchCoursesByBatch(String programmeBatchId) {
        System.out.println("[AcademicService] getProgrammeBatchCoursesByProgrammeBatch called | programmeBatchId: " + programmeBatchId);
        String targetBatchId = programmeBatchId;
        if (targetBatchId != null && !targetBatchId.isBlank()) {
            java.util.Optional<ProgrammeBatch> bByName = programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(targetBatchId.trim());
            if (bByName.isPresent()) {
                targetBatchId = bByName.get().getId();
            }
        }
        CurrentUserScope scope = getScope();
        List<ProgrammeBatchCourse> offerings;
        if (scope != null && scope.isFaculty()) {
            List<ProgrammeBatchCourse> list = (targetBatchId != null && !targetBatchId.isBlank())
                    ? programmeBatchCourseRepository.findByProgrammeBatchId(targetBatchId)
                    : programmeBatchCourseRepository.findAll();
            offerings = list.stream()
                    .filter(o -> {
                        boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                        boolean isAssigned = isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
                        return isAssigned && isCourseAllocationApproved(o);
                    })
                    .collect(Collectors.toList());
        } else if (targetBatchId != null && !targetBatchId.isBlank()) {
            enforceBatchScope(targetBatchId);
            offerings = programmeBatchCourseRepository.findByProgrammeBatchId(targetBatchId);
        } else if (scope != null && scope.isProgrammeCoordinator()) {
            List<ProgrammeBatch> batches = getAllBatches();
            Set<String> bIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            offerings = bIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdIn(bIds);
        } else if (scope != null && scope.isHod()) {
            List<MasterProgramme> progs = getAllProgrammes();
            List<String> pIds = progs.stream().map(MasterProgramme::getId).toList();
            List<ProgrammeBatch> batches = pIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(pIds);
            Set<String> bIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            offerings = bIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdIn(bIds);
        } else if (scope != null && scope.isDirector()) {
            List<Department> depts = departmentRepository.findBySchoolId(scope.getRequiredSchoolId());
            List<String> dIds = depts.stream().map(Department::getId).toList();
            List<MasterProgramme> progs = dIds.isEmpty() ? Collections.emptyList() : masterProgrammeRepository.findByDepartmentIdInAndDeletedAtIsNull(dIds);
            List<String> pIds = progs.stream().map(MasterProgramme::getId).toList();
            List<ProgrammeBatch> batches = pIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(pIds);
            Set<String> bIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            offerings = bIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdIn(bIds);
        } else if (scope != null && scope.isIqac()) {
            offerings = programmeBatchCourseRepository.findAll();
        } else {
            offerings = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId) : programmeBatchCourseRepository.findAll();
        }
        offerings.forEach(this::enrichOffering);
        return offerings;
    }

    @Transactional(readOnly = true)
    public ProgrammeBatchCourse getProgrammeBatchCourseById(String offeringId) {
        System.out.println("[AcademicService] getProgrammeBatchCourseById called | offeringId: " + offeringId);
        if (offeringId == null || offeringId.isBlank()) return null;
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
        enforceProgrammeBatchCourseScope(offering.getId());
        return enrichOffering(offering);
    }

    @Transactional
    public ProgrammeBatchCourse createCourseOffering(com.dypiu.nba.dto.CourseOfferingRequestDto requestDto) {
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course offering request cannot be null.");
        }
        String rawProgrammeBatchId = requestDto.getProgrammeBatchId();
        if (rawProgrammeBatchId == null || rawProgrammeBatchId.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "programmeBatchId is required.");
        }
        final String programmeBatchId = rawProgrammeBatchId.trim();

        String code = requestDto.getEffectiveCode();
        String name = requestDto.getEffectiveName();
        String rawMasterCourseId = requestDto.getMasterCourseId();
        if (rawMasterCourseId != null && !rawMasterCourseId.trim().isBlank()) {
            Optional<ProgrammeBatchCourse> srcOpt = programmeBatchCourseRepository.findById(rawMasterCourseId.trim());
            if (srcOpt.isPresent()) {
                ProgrammeBatchCourse src = srcOpt.get();
                if (code == null || code.isBlank()) code = src.getCode();
                if (name == null || name.isBlank()) name = src.getName();
                if (requestDto.getCredits() == null) requestDto.setCredits(src.getCredits());
                if (requestDto.getCourseType() == null) requestDto.setCourseType(src.getCourseType());
            } else if (code == null) {
                code = rawMasterCourseId.trim();
            }
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course code is required.");
        }
        final String finalCode = code.trim();

        if (requestDto.getSemester() == null || requestDto.getSemester() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semester is required and must be at least 1.");
        }

        enforceBatchScope(programmeBatchId);
        enforceSemesterAllocationEditability(programmeBatchId, requestDto.getSemester());

        // Verify programme batch exists
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + programmeBatchId));

        // Check duplicate offering for active ones
        if (programmeBatchCourseRepository.existsByProgrammeBatchIdAndCodeIgnoreCaseAndDeletedAtIsNull(programmeBatchId, finalCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course offering already exists for batch '" + programmeBatchId + "' and course code '" + finalCode + "'.");
        }

        String codeOverride = cleanOverride(requestDto.getCourseCodeOverride());
        String nameOverride = cleanOverride(requestDto.getCourseNameOverride());

        String assignedFacultyStr = formatAssignedFaculty(requestDto.getAssignedFaculty());

        String coordEmail = requestDto.getCourseCoordinatorEmail() != null && !requestDto.getCourseCoordinatorEmail().isBlank()
                ? requestDto.getCourseCoordinatorEmail().trim()
                : (requestDto.getCoordinatorEmail() != null && !requestDto.getCoordinatorEmail().isBlank() ? requestDto.getCoordinatorEmail().trim() : null);

        if (coordEmail == null && assignedFacultyStr != null && assignedFacultyStr.contains("@")) {
            String firstFaculty = assignedFacultyStr.split("[,;]")[0].trim();
            if (firstFaculty.contains("@")) {
                coordEmail = firstFaculty;
            }
        }

        Long coordinatorId = requestDto.getCourseCoordinatorId();
        String coordinatorName = requestDto.getCourseCoordinatorName() != null && !requestDto.getCourseCoordinatorName().isBlank()
                ? requestDto.getCourseCoordinatorName().trim()
                : (requestDto.getCoordinator() != null && !requestDto.getCoordinator().isBlank() ? requestDto.getCoordinator().trim() : null);

        if (coordEmail != null) {
            String finalCoordEmail = coordEmail;
            User coordUser = userRepository.findByEmailIgnoreCase(finalCoordEmail)
                    .or(() -> userRepository.findByUsernameIgnoreCase(finalCoordEmail))
                    .orElse(null);
            if (coordUser != null) {
                coordinatorId = coordUser.getId();
                if (coordinatorName == null || coordinatorName.isBlank()) {
                    coordinatorName = coordUser.getName();
                }
            }
        } else if (coordinatorId != null) {
            User coordUser = userRepository.findById(coordinatorId).orElse(null);
            if (coordUser != null && (coordinatorName == null || coordinatorName.isBlank())) {
                coordinatorName = coordUser.getName();
            }
        }

        Optional<ProgrammeBatchCourse> softDeletedOfferingOpt = programmeBatchCourseRepository.findFirstByProgrammeBatchIdAndCodeIgnoreCaseAndDeletedAtIsNull(programmeBatchId, finalCode);
        ProgrammeBatchCourse offering;
        boolean isNew = true;
        if (softDeletedOfferingOpt.isPresent() && softDeletedOfferingOpt.get().getDeletedAt() != null) {
            offering = softDeletedOfferingOpt.get();
            offering.setDeletedAt(null);
            offering.setDeletedBy(null);
            offering.setStatus("ACTIVE");
            isNew = false;
        } else {
            offering = ProgrammeBatchCourse.builder()
                    .id("offering-" + UUID.randomUUID().toString().substring(0, 8))
                    .programmeBatchId(programmeBatchId)
                    .code(finalCode)
                    .name(name != null && !name.isBlank() ? name.trim() : finalCode)
                    .credits(requestDto.getCredits() != null ? requestDto.getCredits() : 3)
                    .courseType(requestDto.getCourseType() != null ? requestDto.getCourseType() : "THEORY")
                    .semester(requestDto.getSemester() != null ? requestDto.getSemester() : 1)
                    .status("ACTIVE")
                    .build();
        }

        offering.setSemester(requestDto.getSemester() != null ? requestDto.getSemester() : (offering.getSemester() != null ? offering.getSemester() : 1));
        offering.setCourseCoordinatorId(coordinatorId);
        offering.setCourseCoordinatorName(coordinatorName);
        offering.setCourseCoordinatorEmail(coordEmail);
        offering.setAssignedFaculty(assignedFacultyStr);
        offering.setCourseCodeOverride(codeOverride);
        offering.setCourseNameOverride(nameOverride);
        if (codeOverride != null) offering.setCode(codeOverride);
        if (nameOverride != null) offering.setName(nameOverride);

        ProgrammeBatchCourse saved = programmeBatchCourseRepository.save(offering);
        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    isNew ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH_COURSE,
                    saved.getId(),
                    null,
                    "ACTIVE",
                    isNew ? "Created ProgrammeBatchCourse offering with overrides" : "Reactivated ProgrammeBatchCourse offering with overrides",
                    java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "programmeBatchId", saved.getProgrammeBatchId())
            );
        }
        return enrichOffering(saved);
    }

    @Transactional
    public ProgrammeBatchCourse updateCourseOffering(String offeringId, com.dypiu.nba.dto.CourseOfferingRequestDto requestDto) {
        if (offeringId == null || offeringId.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offering ID is required.");
        }
        if (requestDto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course offering request cannot be null.");
        }
        ProgrammeBatchCourse existing = programmeBatchCourseRepository.findById(offeringId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course offering not found: " + offeringId));

        if (existing.getProgrammeBatchId() != null) enforceBatchScope(existing.getProgrammeBatchId());
        enforceCourseScope(existing.getId());
        if (existing.getProgrammeBatchId() != null && existing.getSemester() != null) {
            enforceSemesterAllocationEditability(existing.getProgrammeBatchId(), existing.getSemester());
        }

        String targetProgrammeBatchId = existing.getProgrammeBatchId();
        if (requestDto.getProgrammeBatchId() != null && !requestDto.getProgrammeBatchId().trim().isBlank()) {
            targetProgrammeBatchId = requestDto.getProgrammeBatchId().trim();
            enforceBatchScope(targetProgrammeBatchId);
            if (!programmeBatchRepository.existsById(targetProgrammeBatchId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + targetProgrammeBatchId);
            }
            existing.setProgrammeBatchId(targetProgrammeBatchId);
        }

        if (requestDto.getCode() != null && !requestDto.getCode().trim().isBlank()) {
            String newCode = requestDto.getCode().trim();
            if (programmeBatchCourseRepository.existsByProgrammeBatchIdAndCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(targetProgrammeBatchId, newCode, existing.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course offering already exists for batch '" + targetProgrammeBatchId + "' and course code '" + newCode + "'.");
            }
            existing.setCode(newCode);
        }

        if (requestDto.getName() != null && !requestDto.getName().trim().isBlank()) {
            existing.setName(requestDto.getName().trim());
        }
        if (requestDto.getCredits() != null) {
            existing.setCredits(requestDto.getCredits());
        }
        if (requestDto.getCourseType() != null) {
            existing.setCourseType(requestDto.getCourseType());
        }

        if (requestDto.getCourseCodeOverride() != null) {
            String cleanCode = cleanOverride(requestDto.getCourseCodeOverride());
            existing.setCourseCodeOverride(cleanCode);
        }
        if (requestDto.getCourseNameOverride() != null) {
            String cleanName = cleanOverride(requestDto.getCourseNameOverride());
            existing.setCourseNameOverride(cleanName);
        }
        if (requestDto.getSemester() != null) {
            if (requestDto.getSemester() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "semester must be >= 1.");
            }
            existing.setSemester(requestDto.getSemester());
        }

        String updateAssignedFacultyStr = requestDto.getAssignedFaculty() != null
                ? formatAssignedFaculty(requestDto.getAssignedFaculty())
                : existing.getAssignedFaculty();

        String updateCoordEmail = requestDto.getCourseCoordinatorEmail() != null && !requestDto.getCourseCoordinatorEmail().isBlank()
                ? requestDto.getCourseCoordinatorEmail().trim()
                : (requestDto.getCoordinatorEmail() != null && !requestDto.getCoordinatorEmail().isBlank() ? requestDto.getCoordinatorEmail().trim() : null);

        if (updateCoordEmail == null && updateAssignedFacultyStr != null && updateAssignedFacultyStr.contains("@")) {
            String firstFaculty = updateAssignedFacultyStr.split("[,;]")[0].trim();
            if (firstFaculty.contains("@")) {
                updateCoordEmail = firstFaculty;
            }
        }

        if (updateCoordEmail != null) {
            String finalCoordEmail = updateCoordEmail;
            User coordUser = userRepository.findByEmailIgnoreCase(finalCoordEmail)
                    .or(() -> userRepository.findByUsernameIgnoreCase(finalCoordEmail))
                    .orElse(null);
            if (coordUser != null) {
                existing.setCourseCoordinatorId(coordUser.getId());
                existing.setCourseCoordinatorName(coordUser.getName());
            }
        } else if (requestDto.getCourseCoordinatorId() != null) {
            existing.setCourseCoordinatorId(requestDto.getCourseCoordinatorId());
            User coordUser = userRepository.findById(requestDto.getCourseCoordinatorId()).orElse(null);
            if (coordUser != null) {
                existing.setCourseCoordinatorName(coordUser.getName());
            }
        }
        if (requestDto.getCourseCoordinatorName() != null && !requestDto.getCourseCoordinatorName().isBlank()) {
            existing.setCourseCoordinatorName(requestDto.getCourseCoordinatorName().trim());
        }
        if (requestDto.getAssignedFaculty() != null) {
            existing.setAssignedFaculty(updateAssignedFacultyStr);
        }

        ProgrammeBatchCourse saved = programmeBatchCourseRepository.save(existing);
        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH_COURSE,
                    saved.getId(),
                    null,
                    saved.getStatus(),
                    "Updated ProgrammeBatchCourse offering",
                    java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "programmeBatchId", saved.getProgrammeBatchId())
            );
        }
        return enrichOffering(saved);
    }

    @Transactional
    public ProgrammeBatchCourse saveProgrammeBatchCourse(ProgrammeBatchCourse offering) {
        System.out.println("[AcademicService] saveProgrammeBatchCourse called | id: " + (offering != null ? offering.getId() : "null"));
        if (offering == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course offering details cannot be null.");
        }
        if (offering.getProgrammeBatchId() != null) {
            enforceBatchScope(offering.getProgrammeBatchId());
            batchLifecycleService.enforceBatchEditability(offering.getProgrammeBatchId());
            if (offering.getSemester() != null) {
                enforceSemesterAllocationEditability(offering.getProgrammeBatchId(), offering.getSemester());
            }
        }
        if (offering.getId() != null) {
            enforceCourseScope(offering.getId());
            ProgrammeBatchCourse existing = programmeBatchCourseRepository.findById(offering.getId()).orElse(null);
            if (existing != null) {
                if (existing.getProgrammeBatchId() != null) {
                    enforceBatchScope(existing.getProgrammeBatchId());
                    if (existing.getSemester() != null) {
                        enforceSemesterAllocationEditability(existing.getProgrammeBatchId(), existing.getSemester());
                    }
                }
            }
        }
        if (offering.getId() == null || offering.getId().isBlank()) {
            offering.setId("offering-" + UUID.randomUUID().toString().substring(0, 8));
        }
        boolean isNew = !programmeBatchCourseRepository.existsById(offering.getId());
        if (isNew && (offering.getStatus() == null || offering.getStatus().isBlank())) {
            offering.setStatus("ACTIVE");
        }
        offering.setCourseCodeOverride(cleanOverride(offering.getCourseCodeOverride()));
        offering.setCourseNameOverride(cleanOverride(offering.getCourseNameOverride()));

        if (offering.getCode() != null && !offering.getCode().isBlank()) {
            offering.setCode(cleanOverride(offering.getCode()));
        } else if (offering.getCourseCodeOverride() != null) {
            offering.setCode(offering.getCourseCodeOverride());
        }

        if (offering.getName() != null && !offering.getName().isBlank()) {
            offering.setName(cleanOverride(offering.getName()));
        } else if (offering.getCourseNameOverride() != null) {
            offering.setName(offering.getCourseNameOverride());
        }

        if (offering.getCredits() == null) {
            offering.setCredits(3);
        }
        if (offering.getCourseType() == null || offering.getCourseType().isBlank()) {
            offering.setCourseType("THEORY");
        }

        if (offering.getProgrammeBatchId() != null && offering.getCode() != null && !offering.getCode().isBlank()) {
            boolean exists = programmeBatchCourseRepository.existsByProgrammeBatchIdAndCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(
                    offering.getProgrammeBatchId(), offering.getCode().trim(), offering.getId());
            if (exists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Course code '" + offering.getCode() + "' already exists in this batch.");
            }
        }

        ProgrammeBatchCourse saved = programmeBatchCourseRepository.save(offering);
        if (auditLogService != null) {
            auditLogService.recordSuccess(isNew ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE, com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH_COURSE, saved.getId(), null, saved.getStatus(), isNew ? "Created ProgrammeBatchCourse" : "Updated ProgrammeBatchCourse", java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "programmeBatchId", saved.getProgrammeBatchId() != null ? saved.getProgrammeBatchId() : ""));
        }
        return enrichOffering(saved);
    }

    @Transactional
    public void deleteProgrammeBatchCourse(String id) {
        System.out.println("[AcademicService] deleteProgrammeBatchCourse called | id: " + id);
        ProgrammeBatchCourse offering = programmeBatchCourseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MasterCourse offering not found: " + id));
        if (offering.getProgrammeBatchId() != null) {
            enforceBatchScope(offering.getProgrammeBatchId());
            if (offering.getSemester() != null) {
                enforceSemesterAllocationEditability(offering.getProgrammeBatchId(), offering.getSemester());
            }
        }
        if (offering.getMasterCourseId() != null) enforceCourseScope(offering.getMasterCourseId());
        CurrentUserScope scope = getScope();
        offering.setDeletedAt(ZonedDateTime.now());
        offering.setDeletedBy(scope != null ? (scope.getEmail() != null ? scope.getEmail() : scope.getUsername()) : "SYSTEM");
        programmeBatchCourseRepository.save(offering);
    }

    // --- Director School Summary ---
    @Transactional(readOnly = true)
    public DirectorSchoolSummaryDto getDirectorSchoolSummary(String directorEmail) {
        System.out.println("[AcademicService] Starting school summary fetch for directorEmail: " + directorEmail);
        CurrentUserScope scope = getScope();
        Optional<School> schoolOpt = Optional.empty();

        if (scope != null && scope.isDirector()) {
            schoolOpt = schoolRepository.findById(scope.getRequiredSchoolId());
        } else {
            if (directorEmail != null && !directorEmail.isBlank()) {
                String cleanEmail = directorEmail.trim();
                schoolOpt = schoolRepository.findByDirectorEmailIgnoreCase(cleanEmail);
                if (schoolOpt.isEmpty()) {
                    Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        if (user.getSchoolId() != null && !user.getSchoolId().isBlank()) {
                            schoolOpt = schoolRepository.findById(user.getSchoolId());
                        }
                        if (schoolOpt.isEmpty() && user.getId() != null) {
                            schoolOpt = schoolRepository.findByDirectorId(user.getId());
                        }
                    }
                }
            }
            if (schoolOpt.isEmpty() && scope != null && scope.getSchoolId() != null) {
                schoolOpt = schoolRepository.findById(scope.getSchoolId());
            }
        }

        if (schoolOpt.isEmpty()) {
            System.out.println("[AcademicService] No school found in database.");
            return DirectorSchoolSummaryDto.builder()
                    .schoolId(null)
                    .schoolName(null)
                    .schoolCode(null)
                    .directorName(null)
                    .directorEmail(directorEmail)
                    .estYear(null)
                    .totalDepartments(0)
                    .assignedHODsCount(0)
                    .unassignedHODsCount(0)
                    .totalProgrammes(0)
                    .build();
        }

        School school = schoolOpt.get();
        List<Department> departments = departmentRepository.findBySchoolId(school.getId());
        List<String> deptIds = departments.stream().map(Department::getId).toList();
        List<MasterProgramme> schoolProgrammes = deptIds.isEmpty() ? Collections.emptyList() : masterProgrammeRepository.findByDepartmentIdInAndDeletedAtIsNull(deptIds);

        int assignedHodCount = 0;
        int unassignedHodCount = 0;

        for (Department dept : departments) {
            boolean isHodAssigned = dept.getHod() != null && !dept.getHod().isBlank() && !dept.getHod().equalsIgnoreCase("Unassigned");
            if (isHodAssigned) {
                assignedHodCount++;
            } else {
                unassignedHodCount++;
            }
        }

        String dName = school.getDirectorName() != null && !school.getDirectorName().isBlank()
                ? school.getDirectorName()
                : school.getDirector();

        DirectorSchoolSummaryDto summary = DirectorSchoolSummaryDto.builder()
                .schoolId(school.getId())
                .schoolName(school.getName())
                .schoolCode(school.getCode())
                .directorName(dName)
                .directorEmail(school.getDirectorEmail())
                .estYear(school.getEstYear())
                .totalDepartments(departments.size())
                .assignedHODsCount(assignedHodCount)
                .unassignedHODsCount(unassignedHodCount)
                .totalProgrammes(schoolProgrammes.size())
                .build();

        System.out.println("[AcademicService] Fetched director school summary for school: " + school.getName() + " (ID: " + school.getId() + ")");
        return summary;
    }

    // --- Director Department Summary ---
    @Transactional(readOnly = true)
    public List<DepartmentSummaryDto> getDepartmentSummary(String schoolId, String directorEmail) {
        System.out.println("[AcademicService] getDepartmentSummary called | schoolId: " + schoolId + " | directorEmail: " + directorEmail);
        CurrentUserScope scope = getScope();
        String targetSchoolId = null;

        if (scope != null && scope.isDirector()) {
            targetSchoolId = scope.getRequiredSchoolId();
        } else if (schoolId != null && !schoolId.isBlank() && !schoolId.equals("sch-1")) {
            targetSchoolId = schoolId;
        } else if (directorEmail != null && !directorEmail.isBlank()) {
            Optional<School> schOpt = schoolRepository.findByDirectorEmailIgnoreCase(directorEmail.trim());
            if (schOpt.isPresent()) {
                targetSchoolId = schOpt.get().getId();
            }
        } else if (scope != null && scope.getSchoolId() != null) {
            targetSchoolId = scope.getSchoolId();
        }

        if (targetSchoolId == null) {
            return Collections.emptyList();
        }

        enforceSchoolScope(targetSchoolId);

        List<Department> departments = departmentRepository.findBySchoolId(targetSchoolId);

        List<DepartmentSummaryDto> list = new ArrayList<>();
        for (Department dept : departments) {
            boolean isHodAssigned = dept.getHod() != null && !dept.getHod().isBlank() && !dept.getHod().equalsIgnoreCase("Unassigned");
            int progsCount = masterProgrammeRepository.findByDepartmentIdAndDeletedAtIsNull(dept.getId()).size();
            list.add(DepartmentSummaryDto.builder()
                    .deptId(dept.getId())
                    .deptCode(dept.getCode())
                    .deptName(dept.getName())
                    .deptHodName(dept.getHod())
                    .deptHodEmail(dept.getHodEmail())
                    .hodAssignedStatus(isHodAssigned)
                    .programmesCount(progsCount)
                    .build());
        }

        System.out.println("[AcademicService] Fetched department summary list (" + list.size() + " items) for schoolId: " + targetSchoolId);
        return list;
    }

    // --- Director Setup Progress ---
    @Transactional(readOnly = true)
    public DirectorSetupProgressDto getDirectorSetupProgress(String schoolId, String directorEmail) {
        System.out.println("[AcademicService] getDirectorSetupProgress called | schoolId: " + schoolId + " | directorEmail: " + directorEmail);
        CurrentUserScope scope = getScope();
        if (schoolId != null && !schoolId.isBlank()) {
            enforceSchoolScope(schoolId.trim());
        }

        String targetSchoolId = null;

        if (scope != null && scope.isDirector()) {
            targetSchoolId = scope.getRequiredSchoolId();
        } else if (schoolId != null && !schoolId.isBlank() && !schoolId.equals("sch-1")) {
            targetSchoolId = schoolId;
        } else if (directorEmail != null && !directorEmail.isBlank()) {
            Optional<School> schOpt = schoolRepository.findByDirectorEmailIgnoreCase(directorEmail.trim());
            if (schOpt.isPresent()) {
                targetSchoolId = schOpt.get().getId();
            }
        } else if (scope != null && scope.getSchoolId() != null) {
            targetSchoolId = scope.getSchoolId();
        }

        if (targetSchoolId == null) {
            return null;
        }

        enforceSchoolScope(targetSchoolId);

        final String finalSchoolId = targetSchoolId;
        DirectorSetupProgress progress = directorSetupProgressRepository.findBySchoolId(finalSchoolId)
                .orElseGet(() -> DirectorSetupProgress.builder()
                        .id("progress-" + finalSchoolId)
                        .schoolId(finalSchoolId)
                        .currentStep(1)
                        .currentStepEnum(DirectorSetupStep.SCHOOL)
                        .overallStatus(SetupStepStatus.IN_PROGRESS)
                        .completedSteps("")
                        .pendingSteps("school,department,programme,review")
                        .updatedAt(ZonedDateTime.now())
                        .build());

        DirectorSetupProgressDto dto = buildSetupProgressDto(progress);
        System.out.println("[AcademicService] Fetched director setup progress for schoolId: " + finalSchoolId + " at step: " + progress.getCurrentStep() + ", completedSteps: " + dto.getCompletedSteps());
        return dto;
    }

    private String normalizeDirectorStepName(Object obj) {
        if (obj == null) return null;
        String s = String.valueOf(obj).trim().toLowerCase();
        if (s.equals("1") || s.equals("school") || s.equals("school_structure")) return "school";
        if (s.equals("2") || s.equals("department") || s.equals("department_management")) return "department";
        if (s.equals("3") || s.equals("programme") || s.equals("programme_overview")) return "programme";
        if (s.equals("4") || s.equals("review") || s.equals("governance")) return "review";
        return s;
    }

    private DirectorSetupStep toDirectorSetupStep(int stepNumber) {
        switch (stepNumber) {
            case 2: return DirectorSetupStep.DEPARTMENT;
            case 3: return DirectorSetupStep.PROGRAMME;
            case 4: return DirectorSetupStep.REVIEW;
            default: return DirectorSetupStep.SCHOOL;
        }
    }

    @Transactional
    public DirectorSetupProgressDto updateDirectorSetupProgress(
            String schoolId,
            Integer stepNumber) {
        return updateDirectorSetupProgress(schoolId, stepNumber, null, null);
    }

    @Transactional
    public DirectorSetupProgressDto updateDirectorSetupProgress(
            String schoolId,
            Integer targetStep,
            String completedStep,
            List<String> completedStepsList) {
        System.out.println("[AcademicService] updateDirectorSetupProgress called | schoolId: " + schoolId + " | targetStep: " + targetStep + " | completedStep: " + completedStep + " | completedStepsList: " + completedStepsList);

        CurrentUserScope scope = getScope();
        if (schoolId != null && !schoolId.isBlank()) {
            enforceSchoolScope(schoolId.trim());
        }

        String targetSchoolId = null;
        if (scope != null && scope.isDirector()) {
            targetSchoolId = scope.getRequiredSchoolId();
        } else if (schoolId != null && !schoolId.isBlank() && !schoolId.equals("sch-1")) {
            targetSchoolId = schoolId;
        } else if (scope != null && scope.getSchoolId() != null) {
            targetSchoolId = scope.getSchoolId();
        }

        if (targetSchoolId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School scope cannot be determined.");
        }

        enforceSchoolScope(targetSchoolId);

        final String finalSchoolId = targetSchoolId;
        DirectorSetupProgress progress =
                directorSetupProgressRepository
                        .findBySchoolId(finalSchoolId)
                        .orElseGet(() -> DirectorSetupProgress.builder()
                                .id("progress-" + finalSchoolId)
                                .schoolId(finalSchoolId)
                                .build());

        Set<String> existingCompleted = new LinkedHashSet<>();
        if (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank()) {
            for (String s : progress.getCompletedSteps().split(",")) {
                String norm = normalizeDirectorStepName(s);
                if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
            }
        }

        // Add newly completed step(s)
        if (completedStepsList != null && !completedStepsList.isEmpty()) {
            for (Object item : completedStepsList) {
                String norm = normalizeDirectorStepName(item);
                if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
            }
        } else if (completedStep != null && !completedStep.isBlank()) {
            String norm = normalizeDirectorStepName(completedStep);
            if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
        } else if (targetStep != null) {
            String norm = normalizeDirectorStepName(targetStep);
            if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
        }

        List<String> ALL_STEPS = List.of("school", "department", "programme", "review");
        List<String> completed = ALL_STEPS.stream().filter(existingCompleted::contains).toList();
        List<String> pending = ALL_STEPS.stream().filter(s -> !existingCompleted.contains(s)).toList();

        int currentStep = (targetStep != null && targetStep >= 1 && targetStep <= 4)
                ? targetStep
                : (progress.getCurrentStep() != null ? progress.getCurrentStep() : 1);

        DirectorSetupStep stepEnum = toDirectorSetupStep(currentStep);

        SetupStepStatus overallStatus;
        if (completed.size() == ALL_STEPS.size()) {
            overallStatus = SetupStepStatus.COMPLETED;
        } else if (completed.isEmpty()) {
            overallStatus = SetupStepStatus.NOT_STARTED;
        } else {
            overallStatus = SetupStepStatus.IN_PROGRESS;
        }

        progress.setCurrentStep(currentStep);
        progress.setCurrentStepEnum(stepEnum);
        progress.setOverallStatus(overallStatus);
        progress.setCompletedSteps(String.join(",", completed));
        progress.setPendingSteps(String.join(",", pending));
        progress.setUpdatedAt(ZonedDateTime.now());

        directorSetupProgressRepository.save(progress);

        DirectorSetupProgressDto dto = buildSetupProgressDto(progress);
        System.out.println(
                "[AcademicService] Director setup progress updated | " +
                        "schoolId=" + targetSchoolId +
                        " | currentStep=" + currentStep +
                        " | completed=" + completed +
                        " | pending=" + pending +
                        " | overallStatus=" + overallStatus
        );

        return dto;
    }

    private DirectorSetupProgressDto buildSetupProgressDto(DirectorSetupProgress progress) {
        List<String> completedList = (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank())
                ? Arrays.asList(progress.getCompletedSteps().split(","))
                : Collections.emptyList();

        List<String> pendingList = (progress.getPendingSteps() != null && !progress.getPendingSteps().isBlank())
                ? Arrays.asList(progress.getPendingSteps().split(","))
                : Collections.emptyList();

        Map<DirectorSetupStep, SetupStepStatus> stepStatuses = new EnumMap<>(DirectorSetupStep.class);
        int currentStep = progress.getCurrentStep();

        boolean isSchoolDone = completedList.contains("school");
        boolean isDeptDone = completedList.contains("department");
        boolean isProgDone = completedList.contains("programme");
        boolean isReviewDone = completedList.contains("review");

        stepStatuses.put(DirectorSetupStep.SCHOOL, isSchoolDone ? SetupStepStatus.COMPLETED : (currentStep == 1 ? SetupStepStatus.IN_PROGRESS : SetupStepStatus.NOT_STARTED));
        stepStatuses.put(DirectorSetupStep.DEPARTMENT, isDeptDone ? SetupStepStatus.COMPLETED : (currentStep == 2 ? SetupStepStatus.IN_PROGRESS : SetupStepStatus.NOT_STARTED));
        stepStatuses.put(DirectorSetupStep.PROGRAMME, isProgDone ? SetupStepStatus.COMPLETED : (currentStep == 3 ? SetupStepStatus.IN_PROGRESS : SetupStepStatus.NOT_STARTED));
        stepStatuses.put(DirectorSetupStep.REVIEW, isReviewDone ? SetupStepStatus.COMPLETED : SetupStepStatus.NOT_STARTED);

        return DirectorSetupProgressDto.builder()
                .currentStep(progress.getCurrentStep())
                .currentStepEnum(progress.getCurrentStepEnum())
                .overallStatus(progress.getOverallStatus())
                .completedSteps(completedList)
                .pendingSteps(pendingList)
                .stepStatuses(stepStatuses)
                .schoolId(progress.getSchoolId())
                .build();
    }

    // --- Schools ---
    @Transactional(readOnly = true)
    public List<School> getAllSchools() {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) {
            return schoolRepository.findAll();
        }
        if (scope.hasSchoolScope()) {
            return schoolRepository.findById(scope.getSchoolId())
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public School getSchoolById(String id) {
        System.out.println("[AcademicService] getSchoolById called | id: " + id);
        enforceSchoolScope(id);
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + id));
        System.out.println("[AcademicService] Fetched school by id: " + id);
        return school;
    }

    @Transactional
    public School saveSchool(School school) {
        System.out.println("[AcademicService] saveSchool called | school: " + (school != null ? school.getName() : "null"));
        if (school == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School details cannot be null.");
        }
        if (school.getId() != null) {
            enforceSchoolScope(school.getId());
        }

        // 1. Check if directorId is already mapped to another school
        if (school.getDirectorId() != null) {
            Optional<School> existingByDirectorId = schoolRepository.findByDirectorId(school.getDirectorId());
            if (existingByDirectorId.isPresent()) {
                School existing = existingByDirectorId.get();
                if (school.getId() == null || !existing.getId().equalsIgnoreCase(school.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Director is already assigned to School: " + existing.getName() + " (" + existing.getCode() + "). A Director can only manage one school.");
                }
            }
        }

        // 2. Check if directorEmail is already mapped to another school
        if (school.getDirectorEmail() != null && !school.getDirectorEmail().isBlank()) {
            String cleanEmail = school.getDirectorEmail().trim();
            Optional<School> existingByEmail = schoolRepository.findByDirectorEmailIgnoreCase(cleanEmail);
            if (existingByEmail.isPresent()) {
                School existing = existingByEmail.get();
                if (school.getId() == null || !existing.getId().equalsIgnoreCase(school.getId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Director Email '" + cleanEmail + "' is already assigned to School: " + existing.getName() + " (" + existing.getCode() + "). A Director can only manage one school.");
                }
            }

            // Sync directorId and directorName from User entity if available
            userRepository.findByEmail(cleanEmail).ifPresent(u -> {
                if (school.getDirectorId() == null) {
                    school.setDirectorId(u.getId());
                }
                if (school.getDirectorName() == null || school.getDirectorName().isBlank()) {
                    school.setDirectorName(u.getName());
                }
            });
        }

        // Auto-generate school ID if missing
        if (school.getId() == null || school.getId().isBlank()) {
            school.setId(UUID.randomUUID().toString());
        }

        boolean isNewSchool = !schoolRepository.existsById(school.getId());
        School saved = schoolRepository.save(school);

        if (saved.getDirectorEmail() != null && !saved.getDirectorEmail().isBlank()) {
            String cleanEmail = saved.getDirectorEmail().trim();
            userRepository.findByEmail(cleanEmail).ifPresent(u -> {
                u.setSchoolId(saved.getId());
                userRepository.save(u);
            });
        }

        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    isNewSchool ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.SCHOOL,
                    saved.getId(),
                    null,
                    "ACTIVE",
                    isNewSchool ? "Created School" : "Updated School",
                    java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "name", saved.getName() != null ? saved.getName() : "")
            );
        }

        System.out.println("[AcademicService] School saved successfully with id: " + saved.getId());
        return saved;
    }

    @Transactional
    public School updateSchool(String id, School school) {
        System.out.println("[AcademicService] updateSchool called | id: " + id + " | school: " + (school != null ? school.getName() : "null"));
        enforceSchoolScope(id);
        School existing = schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + id));

        // 1. Check if directorId is already mapped to another school
        if (school.getDirectorId() != null) {
            Optional<School> existingByDirectorId = schoolRepository.findByDirectorId(school.getDirectorId());
            if (existingByDirectorId.isPresent()) {
                School mappedSchool = existingByDirectorId.get();
                if (!mappedSchool.getId().equalsIgnoreCase(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Director is already assigned to School: " + mappedSchool.getName() + " (" + mappedSchool.getCode() + "). A Director can only manage one school.");
                }
            }
        }

        // 2. Check if directorEmail is already mapped to another school
        if (school.getDirectorEmail() != null && !school.getDirectorEmail().isBlank()) {
            String cleanEmail = school.getDirectorEmail().trim();
            Optional<School> existingByEmail = schoolRepository.findByDirectorEmailIgnoreCase(cleanEmail);
            if (existingByEmail.isPresent()) {
                School mappedSchool = existingByEmail.get();
                if (!mappedSchool.getId().equalsIgnoreCase(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Director Email '" + cleanEmail + "' is already assigned to School: " + mappedSchool.getName() + " (" + mappedSchool.getCode() + "). A Director can only manage one school.");
                }
            }
        }

        existing.setCode(school.getCode());
        existing.setName(school.getName());
        existing.setDirector(school.getDirector());
        existing.setDirectorEmail(school.getDirectorEmail());
        existing.setDirectorId(school.getDirectorId());
        existing.setDirectorName(school.getDirectorName());
        existing.setDean(school.getDean());
        existing.setDeanEmail(school.getDeanEmail());
        existing.setEstYear(school.getEstYear());

        School updated = schoolRepository.save(existing);

        if (updated.getDirectorEmail() != null && !updated.getDirectorEmail().isBlank()) {
            String cleanEmail = updated.getDirectorEmail().trim();
            userRepository.findByEmail(cleanEmail).ifPresent(u -> {
                u.setSchoolId(updated.getId());
                userRepository.save(u);
            });
        }

        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.SCHOOL,
                    updated.getId(),
                    null,
                    "ACTIVE",
                    "Updated School",
                    java.util.Map.of("code", updated.getCode() != null ? updated.getCode() : "", "name", updated.getName() != null ? updated.getName() : "")
            );
        }

        System.out.println("[AcademicService] School updated successfully for id: " + updated.getId());
        return updated;
    }

    // --- Departments ---
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        CurrentUserScope scope = getScope();
        if (scope == null || scope.isIqac()) {
            return departmentRepository.findAll();
        }
        if (scope.isDirector()) {
            return departmentRepository.findBySchoolId(scope.getRequiredSchoolId());
        }
        if (scope.isHod()) {
            List<Department> byEmail = (scope.getEmail() != null && !scope.getEmail().isBlank())
                    ? departmentRepository.findByHodEmailIgnoreCase(scope.getEmail().trim())
                    : Collections.emptyList();
            if (byEmail != null && !byEmail.isEmpty()) {
                return byEmail;
            }
            return departmentRepository.findById(scope.getRequiredDepartmentId())
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        if (scope.isProgrammeCoordinator() || scope.isFaculty()) {
            if (scope.hasDepartmentScope()) {
                return departmentRepository.findById(scope.getDepartmentId())
                    .map(List::of)
                    .orElse(Collections.emptyList());
            }
        }
        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Department> getDepartmentsBySchool(String schoolId) {
        CurrentUserScope scope = getScope();
        if (scope != null && !scope.isIqac()) {
            enforceSchoolScope(schoolId);
            if (scope.isDirector()) {
                return departmentRepository.findBySchoolId(scope.getRequiredSchoolId());
            }
            if (scope.isHod() || scope.isProgrammeCoordinator() || scope.isFaculty()) {
                return getAllDepartments().stream()
                        .filter(d -> schoolId != null && schoolId.equalsIgnoreCase(d.getSchoolId()))
                        .toList();
            }
        }
        return departmentRepository.findBySchoolId(schoolId);
    }

    @Transactional(readOnly = true)
    public Department getDepartmentById(String id) {
        System.out.println("[AcademicService] getDepartmentById called | id: " + id);
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        enforceSchoolScope(dept.getSchoolId());
        enforceDepartmentScope(dept.getId());
        return dept;
    }

    @Transactional
    public Department saveDepartment(Department department) {
        System.out.println("[AcademicService] saveDepartment called | department: " + (department != null ? department.getName() : "null"));
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isDirector()) {
            department.setSchoolId(scope.getRequiredSchoolId());
        }
        if (department.getId() != null) {
            Department existing = departmentRepository.findById(department.getId()).orElse(null);
            if (existing != null) {
                enforceSchoolScope(existing.getSchoolId());
                enforceDepartmentScope(existing.getId());
            }
        }
        if (department.getId() == null) department.setId("dept-" + UUID.randomUUID().toString().substring(0, 8));
        boolean isNewDept = (department.getId() == null || !departmentRepository.existsById(department.getId()));
        Department saved = departmentRepository.save(department);
        if (auditLogService != null) {
            auditLogService.recordSuccess(isNewDept ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE, com.dypiu.nba.audit.ResourceType.DEPARTMENT, saved.getId(), null, "ACTIVE", isNewDept ? "Created Department" : "Updated Department", java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "name", saved.getName() != null ? saved.getName() : ""));
        }
        System.out.println("[AcademicService] Saved department with id: " + saved.getId());

        // Sync department info to HOD user if hodEmail or hod name matches
        if (saved.getHodEmail() != null && !saved.getHodEmail().isBlank()) {
            userRepository.findByEmail(saved.getHodEmail()).ifPresent(user -> {
                user.setDepartment(saved.getName());
                user.setDepartmentId(saved.getId());
                if (saved.getSchoolId() != null) {
                    user.setSchoolId(saved.getSchoolId());
                }
                userRepository.save(user);
                System.out.println("[AcademicService] Updated HOD user (" + user.getEmail() + ") department to: " + saved.getName());
            });
        }
        return saved;
    }

    @Transactional
    public void deleteDepartment(String id) {
        System.out.println("[AcademicService] deleteDepartment called | id: " + id);
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        enforceSchoolScope(dept.getSchoolId());
        enforceDepartmentScope(dept.getId());
        departmentRepository.deleteById(id);
        System.out.println("[AcademicService] Deleted department with id: " + id);
    }

    // --- Users by Role ---
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByRole(String role) {
        return getUsersByRole(role, null);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getUsersByRole(String role, String departmentId) {
        System.out.println("[AcademicService] getUsersByRole called | role: " + role + " | departmentId: " + departmentId);
        CurrentUserScope scope = getScope();
        List<User> users;

        if (role == null || role.isBlank() || role.equalsIgnoreCase("ALL")) {
            users = userRepository.findAll();
        } else {
            String searchRole = role.trim().toUpperCase().replace("-", "_");
            if (searchRole.equals("PROGRAMME_COORDINATOR")
                    || searchRole.equals("COORDINATOR")
                    || searchRole.equals("PC")
                    || searchRole.equals("PROGRAMME_COORD")) {
                users = userRepository.findByRole(UserRole.PROGRAMME_COORDINATOR);
            } else if (searchRole.equals("COURSE_COORDINATOR")
                    || searchRole.equals("CC")
                    || searchRole.equals("FACULTY")) {
                users = userRepository.findByRole(UserRole.FACULTY);
            } else if (searchRole.equals("HOD")) {
                users = userRepository.findByRole(UserRole.HOD);
            } else if (searchRole.equals("DIRECTOR")) {
                users = userRepository.findByRole(UserRole.DIRECTOR);
            } else if (searchRole.equals("IQAC")) {
                users = userRepository.findByRole(UserRole.IQAC);
            } else {
                try {
                    UserRole userRole = UserRole.valueOf(searchRole);
                    users = userRepository.findByRole(userRole);
                } catch (IllegalArgumentException e) {
                    users = userRepository.findAll();
                }
            }
        }

        if (departmentId != null && !departmentId.isBlank()) {
            users = users.stream()
                    .filter(u -> u.getDepartmentId() != null && u.getDepartmentId().equalsIgnoreCase(departmentId.trim()))
                    .collect(Collectors.toList());
        }

        // Apply organizational scope isolation for Director, HOD, and MasterProgramme Coordinator
        if (scope != null && scope.isDirector()) {
            String schoolId = scope.getRequiredSchoolId();
            users = users.stream()
                    .filter(u -> u.getSchoolId() != null && u.getSchoolId().equals(schoolId))
                    .collect(Collectors.toList());
        } else if (scope != null && scope.isHod()) {
            String schoolId = scope.getRequiredSchoolId();
            String deptId = scope.getRequiredDepartmentId();
            users = users.stream()
                    .filter(u -> (u.getSchoolId() == null || u.getSchoolId().equals(schoolId))
                            && (u.getDepartmentId() == null || u.getDepartmentId().equals(deptId)))
                    .collect(Collectors.toList());
        } else if (scope != null && scope.isProgrammeCoordinator()) {
            if (scope.hasSchoolScope()) {
                String schoolId = scope.getSchoolId();
                users = users.stream().filter(u -> u.getSchoolId() == null || u.getSchoolId().equals(schoolId)).collect(Collectors.toList());
            }
            if (scope.hasDepartmentScope()) {
                String deptId = scope.getDepartmentId();
                users = users.stream().filter(u -> u.getDepartmentId() == null || u.getDepartmentId().equals(deptId)).collect(Collectors.toList());
            }
        }

        List<UserDto> dtos = users.stream()
                .map(u -> {
                    String resolvedEmail = u.getEmail();
                    if (resolvedEmail == null || resolvedEmail.isBlank()) {
                        if (u.getUsername() != null && u.getUsername().contains("@")) {
                            resolvedEmail = u.getUsername();
                        } else if (u.getUsername() != null && !u.getUsername().isBlank()) {
                            resolvedEmail = u.getUsername() + "@dypiu.ac.in";
                        } else {
                            resolvedEmail = "user" + u.getId() + "@dypiu.ac.in";
                        }
                    }
                    return UserDto.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .name(u.getName() != null && !u.getName().isBlank() ? u.getName() : (u.getUsername() != null ? u.getUsername() : "User " + u.getId()))
                            .email(resolvedEmail)
                            .role(u.getRole() != null
                                    ? u.getRole().name()
                                    : UserRole.FACULTY.name())
                            .schoolId(u.getSchoolId())
                            .departmentId(u.getDepartmentId())
                            .masterProgrammeId(u.getMasterProgrammeId())
                            .department(u.getDepartment())
                            .programme(u.getProgramme())
                            .build();
                })
                .toList();

        System.out.println("[AcademicService] Fetched users by role (" + role + "): count=" + dtos.size());
        return dtos;
    }

    // --- Programmes ---
        @Transactional(readOnly = true)
    private void enrichProgrammeCoordinator(MasterProgramme programme) {
        if (programme == null) return;
        String coordEmail = programme.getCoordinatorEmail();
        String coord = programme.getCoordinator();

        // 1. If coordinatorEmail is valid, lookup user by email
        if (coordEmail != null && !coordEmail.isBlank() && coordEmail.contains("@")) {
            Optional<User> uOpt = userRepository.findByEmail(coordEmail.trim());
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                programme.setCoordinator(u.getName());
                programme.setCoordinatorEmail(u.getEmail());
                return;
            }
        }

        // 1. If coordinatorEmail is valid, lookup user by email
        if (coordEmail != null && !coordEmail.isBlank() && coordEmail.contains("@")) {
            Optional<User> uOpt = userRepository.findByEmail(coordEmail.trim());
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                programme.setCoordinator(u.getName());
                programme.setCoordinatorEmail(u.getEmail());
                return;
            }
        }

        // 2. Lookup by coordinator string (ID, email, username, name)
        // 3. Fallback: check if a user with PROGRAMME_COORDINATOR role is assigned to this programme
        if (programme.getCoordinator() == null || programme.getCoordinator().isBlank() || "Not Assigned".equalsIgnoreCase(programme.getCoordinator())) {
            List<User> pcs = userRepository.findByRole(UserRole.PROGRAMME_COORDINATOR).stream().filter(u -> programme.getId().equalsIgnoreCase(u.getMasterProgrammeId())).toList();
            if (pcs != null && !pcs.isEmpty()) {
                programme.setCoordinator(pcs.get(0).getName());
                programme.setCoordinatorEmail(pcs.get(0).getEmail());
                return;
            }
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(programme.getId());
            for (ProgrammeBatch b : batches) {
                if (b.getCoordinatorName() != null && !b.getCoordinatorName().isBlank()) {
                    programme.setCoordinator(b.getCoordinatorName());
                    programme.setCoordinatorEmail(b.getCoordinatorEmail());
                    return;
                }
            }
        }

        if (coord != null && !coord.isBlank()) {
            if (coord.matches("\\d+")) {
                try {
                    Long userId = Long.parseLong(coord);
                    userRepository.findById(userId).ifPresent(u -> {
                        programme.setCoordinator(u.getName());
                        if (programme.getCoordinatorEmail() == null || programme.getCoordinatorEmail().isBlank()) {
                            programme.setCoordinatorEmail(u.getEmail());
                        }
                    });
                } catch (NumberFormatException ignored) {}
            } else if (coord.contains("@")) {
                userRepository.findByEmail(coord.trim()).ifPresent(u -> {
                    programme.setCoordinator(u.getName());
                    programme.setCoordinatorEmail(u.getEmail());
                });
            } else {
                Optional<User> uOpt = userRepository.findByUsername(coord.trim());
                if (uOpt.isEmpty()) {
                    uOpt = userRepository.findAll().stream()
                            .filter(u -> u.getName() != null && u.getName().trim().equalsIgnoreCase(coord.trim()))
                            .findFirst();
                }
                if (uOpt.isPresent()) {
                    User u = uOpt.get();
                    programme.setCoordinator(u.getName());
                    if (programme.getCoordinatorEmail() == null || programme.getCoordinatorEmail().isBlank()) {
                        programme.setCoordinatorEmail(u.getEmail());
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public CourseCoordinatorSummaryDto getCourseCoordinatorSummary(String coordinatorEmail) {
        return getCourseCoordinatorSummary(coordinatorEmail, null);
    }

    @Transactional(readOnly = true)
    public CourseCoordinatorSummaryDto getCourseCoordinatorSummary(String coordinatorEmail, String selectedCourseOrOfferingId) {
        System.out.println("[AcademicService] getCourseCoordinatorSummary called | coordinatorEmail: " + coordinatorEmail + " | selectedCourseOrOfferingId: " + selectedCourseOrOfferingId);
        CurrentUserScope scope = getScope();
        String name = scope != null && scope.getName() != null ? scope.getName() : "Course Coordinator";
        String email = scope != null && scope.getEmail() != null ? scope.getEmail() : (coordinatorEmail != null ? coordinatorEmail.trim() : "");
        Long userId = scope != null ? scope.getUserId() : null;

        if (scope == null || !scope.isFaculty()) {
            if (!email.isBlank()) {
                Optional<User> uOpt = userRepository.findByEmail(email);
                if (uOpt.isPresent()) {
                    name = uOpt.get().getName();
                    userId = uOpt.get().getId();
                }
            }
        }

        List<ProgrammeBatchCourse> allOfferings = programmeBatchCourseRepository.findAll();
        final String searchEmail = email.toLowerCase();
        final String searchName = name.toLowerCase();

        List<ProgrammeBatchCourse> assignedOfferings = allOfferings.stream()
                .filter(o -> {
                    boolean matches = false;
                    if (o.getCourseCoordinatorId() != null) {
                        User u = userRepository.findById(o.getCourseCoordinatorId()).orElse(null);
                        if (u != null && (u.getName().equalsIgnoreCase(searchName) || u.getEmail().equalsIgnoreCase(searchName))) {
                            matches = true;
                        }
                    }
                    boolean matchFaculty = (o.getAssignedFaculty() != null && (o.getAssignedFaculty().toLowerCase().contains(searchEmail) || (!searchName.isBlank() && o.getAssignedFaculty().toLowerCase().contains(searchName))));
                    boolean assigned = matches || matchFaculty;
                    if (scope != null && scope.isFaculty()) {
                        return assigned && isCourseAllocationApproved(o);
                    }
                    return assigned;
                })
                .toList();

        List<ProgrammeBatchCourse> finalCourses = assignedOfferings.stream().map(this::enrichOffering).toList();

        // Determine target offering for detailed metrics
        ProgrammeBatchCourse targetOffering = null;
        if (selectedCourseOrOfferingId != null && !selectedCourseOrOfferingId.isBlank()) {
            if (programmeBatchCourseRepository.existsById(selectedCourseOrOfferingId.trim())) {
                targetOffering = programmeBatchCourseRepository.findById(selectedCourseOrOfferingId.trim()).orElse(null);
            } else {
                List<ProgrammeBatchCourse> matched = programmeBatchCourseRepository.findByProgrammeBatchId(selectedCourseOrOfferingId.trim());
                if (!matched.isEmpty()) targetOffering = matched.get(0);
            }
        }
        if (targetOffering == null && !assignedOfferings.isEmpty()) {
            targetOffering = assignedOfferings.get(0);
        }
        if (targetOffering != null) {
            targetOffering = enrichOffering(targetOffering);
        }

        String schoolName = null;
        String deptName = null;
        String progName = null;
        String batchName = null;
        int coCount = 0;
        int poCount = 0;
        int psoCount = 0;
        CourseCoordinatorSetupProgressDto setupProgress = null;

        if (targetOffering != null) {
            String programmeBatchId = targetOffering.getProgrammeBatchId();
            if (programmeBatchId != null) {
                poCount = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId).size();
                psoCount = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(programmeBatchId).size();

                ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId).orElse(null);
                if (batch != null) {
                    batchName = batch.getName();
                    String progId = batch.getMasterProgrammeId();
                    if (progId != null) {
                        MasterProgramme prog = masterProgrammeRepository.findByIdAndDeletedAtIsNull(progId).orElse(null);
                        if (prog != null) {
                            progName = prog.getName();
                            String deptId = prog.getDepartmentId();
                            if (deptId != null) {
                                Department dept = departmentRepository.findById(deptId).orElse(null);
                                if (dept != null) {
                                    deptName = dept.getName();
                                    String schId = dept.getSchoolId();
                                    if (schId != null) {
                                        School sch = schoolRepository.findById(schId).orElse(null);
                                        if (sch != null) {
                                            schoolName = sch.getName();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            coCount = courseOutcomeRepository.findByProgrammeBatchCourseId(targetOffering.getId()).size();
            setupProgress = getCourseCoordinatorSetupProgress(email, targetOffering.getId());
        }

        if (schoolName == null && scope != null && scope.getSchoolId() != null) {
            School sch = schoolRepository.findById(scope.getSchoolId()).orElse(null);
            if (sch != null) schoolName = sch.getName();
        }

        if (poCount == 0) poCount = 12;
        if (psoCount == 0) psoCount = 2;

        return CourseCoordinatorSummaryDto.builder()
                .schoolName(schoolName)
                .departmentName(deptName)
                .programmeName(progName)
                .batchName(batchName)
                .programmeBatchCourseId(targetOffering != null ? targetOffering.getId() : null)
                .courseCode(targetOffering != null ? targetOffering.getEffectiveCourseCode() : null)
                .courseName(targetOffering != null ? targetOffering.getEffectiveCourseName() : null)
                .coordinatorName(name)
                .coordinatorEmail(email)
                .assignedCourseCount(finalCourses.size())
                .assignedCourses(finalCourses)
                .setupProgress(setupProgress)
                .courseOutcomesCount(coCount)
                .poCount(poCount)
                .psoCount(psoCount)
                .build();
    }

    private String resolveTargetMasterCourseId(String masterCourseId) {
        return masterCourseId;
    }

    @Transactional(readOnly = true)
    public CourseCoordinatorSetupProgressDto getCourseCoordinatorSetupProgress(String coordinatorEmail, String masterCourseId) {
        return getCourseCoordinatorSetupProgress(coordinatorEmail, masterCourseId, null);
    }

    @Transactional(readOnly = true)
    public CourseCoordinatorSetupProgressDto getCourseCoordinatorSetupProgress(String coordinatorEmail, String masterCourseIdOrOfferingId, String programmeBatchId) {
        String targetId = masterCourseIdOrOfferingId;
        if ((targetId == null || targetId.isBlank()) && programmeBatchId != null && !programmeBatchId.isBlank()) {
            List<ProgrammeBatchCourse> pbcs = programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId);
            if (coordinatorEmail != null && !coordinatorEmail.isBlank()) {
                final String searchEmail = coordinatorEmail.trim().toLowerCase();
                Optional<ProgrammeBatchCourse> matched = pbcs.stream()
                        .filter(o -> {
                            boolean matches = false;
                            if (o.getCourseCoordinatorId() != null) {
                                User u = userRepository.findById(o.getCourseCoordinatorId()).orElse(null);
                                if (u != null && (u.getEmail().equalsIgnoreCase(searchEmail) || u.getName().equalsIgnoreCase(searchEmail))) {
                                    matches = true;
                                }
                            }
                            boolean matchFaculty = (o.getAssignedFaculty() != null && o.getAssignedFaculty().toLowerCase().contains(searchEmail));
                            return matches || matchFaculty;
                        })
                        .findFirst();
                if (matched.isPresent()) {
                    targetId = matched.get().getId();
                } else if (!pbcs.isEmpty()) {
                    targetId = pbcs.get(0).getId();
                }
            } else if (!pbcs.isEmpty()) {
                targetId = pbcs.get(0).getId();
            }
        }
        String targetMasterCourseId = resolveTargetMasterCourseId(targetId);
        System.out.println("[AcademicService] getCourseCoordinatorSetupProgress called | masterCourseId: " + masterCourseIdOrOfferingId + " | batch: " + programmeBatchId + " -> targetMasterCourseId: " + targetMasterCourseId);
        if (targetMasterCourseId != null && !targetMasterCourseId.isBlank()) {
            if (programmeBatchCourseRepository.existsById(targetMasterCourseId)) {
                enforceProgrammeBatchCourseScope(targetMasterCourseId);
            }
        }
        CourseCoordinatorSetupProgress progress = (targetMasterCourseId != null)
                ? ccSetupProgressRepository.findByProgrammeBatchCourseId(targetMasterCourseId).orElseGet(() -> CourseCoordinatorSetupProgress.builder()
                        .id("ccprog-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchCourseId(targetMasterCourseId)
                        .coordinatorEmail(coordinatorEmail)
                        .currentStep(1)
                        .overallStatus(SetupStepStatus.IN_PROGRESS)
                        .completedSteps("")
                        .pendingSteps("cos,co_mapping,direct,indirect,attainment,course_atr")
                        .updatedAt(ZonedDateTime.now())
                        .build())
                : null;
        if (progress == null) {
            return null;
        }

        List<String> completed = (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank())
                ? Arrays.asList(progress.getCompletedSteps().split(","))
                : Collections.emptyList();
        List<String> pending = (progress.getPendingSteps() != null && !progress.getPendingSteps().isBlank())
                ? Arrays.asList(progress.getPendingSteps().split(","))
                : Collections.emptyList();

        return CourseCoordinatorSetupProgressDto.builder()
                .id(progress.getId())
                .programmeBatchCourseId(progress.getProgrammeBatchCourseId())
                .masterCourseId(progress.getProgrammeBatchCourseId())
                .currentStep(progress.getCurrentStep())
                .completedSteps(completed)
                .pendingSteps(pending)
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    @Transactional
    public CourseCoordinatorSetupProgressDto updateCourseCoordinatorSetupProgress(String coordinatorEmail, String masterCourseId, Integer currentStep) {
        return updateCourseCoordinatorSetupProgress(coordinatorEmail, masterCourseId, currentStep, null);
    }

    @Transactional
    public CourseCoordinatorSetupProgressDto updateCourseCoordinatorSetupProgress(
            String coordinatorEmail,
            String masterCourseId,
            Integer currentStep,
            Map<String, Object> body) {
        String effectiveMasterCourseId = masterCourseId;
        Integer effectiveStep = currentStep;
        String effectiveEmail = coordinatorEmail;
        List<String> completedStepsList = null;
        List<String> pendingStepsList = null;

        if (body != null) {
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("masterCourseId")) {
                effectiveMasterCourseId = String.valueOf(body.get("masterCourseId"));
            }
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("offeringId")) {
                effectiveMasterCourseId = String.valueOf(body.get("offeringId"));
            }
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("programmeBatchCourseId")) {
                effectiveMasterCourseId = String.valueOf(body.get("programmeBatchCourseId"));
            }
            if ((effectiveEmail == null || effectiveEmail.isBlank()) && body.containsKey("coordinatorEmail")) {
                effectiveEmail = String.valueOf(body.get("coordinatorEmail"));
            }
            if ((effectiveEmail == null || effectiveEmail.isBlank()) && body.containsKey("email")) {
                effectiveEmail = String.valueOf(body.get("email"));
            }
            if (body.containsKey("stepNumber")) {
                try { effectiveStep = Integer.parseInt(String.valueOf(body.get("stepNumber"))); } catch (Exception ignored) {}
            }
            if (body.containsKey("currentStep")) {
                try { effectiveStep = Integer.parseInt(String.valueOf(body.get("currentStep"))); } catch (Exception ignored) {}
            }
            if (body.containsKey("step")) {
                try { effectiveStep = Integer.parseInt(String.valueOf(body.get("step"))); } catch (Exception ignored) {}
            }
            if (body.get("completedSteps") instanceof List<?> list) {
                completedStepsList = list.stream().map(String::valueOf).toList();
            } else if (body.containsKey("completedSteps") && body.get("completedSteps") != null) {
                completedStepsList = Arrays.asList(String.valueOf(body.get("completedSteps")).split(","));
            }
            if (body.get("pendingSteps") instanceof List<?> list) {
                pendingStepsList = list.stream().map(String::valueOf).toList();
            } else if (body.containsKey("pendingSteps") && body.get("pendingSteps") != null) {
                pendingStepsList = Arrays.asList(String.valueOf(body.get("pendingSteps")).split(","));
            }
        }

        if (effectiveMasterCourseId == null || effectiveMasterCourseId.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course ID is required for setup progress.");
        }
        String targetMasterCourseId = effectiveMasterCourseId.trim();

        System.out.println("[AcademicService] updateCourseCoordinatorSetupProgress called | courseId: " + targetMasterCourseId + " | stepNumber: " + effectiveStep);
        if (programmeBatchCourseRepository.existsById(targetMasterCourseId)) {
            enforceProgrammeBatchCourseScope(targetMasterCourseId);
        }

        final String finalEmail = effectiveEmail;
        CourseCoordinatorSetupProgress progress = ccSetupProgressRepository.findByProgrammeBatchCourseId(targetMasterCourseId)
                .orElseGet(() -> CourseCoordinatorSetupProgress.builder()
                        .id("ccprog-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchCourseId(targetMasterCourseId)
                        .coordinatorEmail(finalEmail)
                        .currentStep(1)
                        .overallStatus(SetupStepStatus.IN_PROGRESS)
                        .completedSteps("")
                        .pendingSteps("cos,co_mapping,direct,indirect,attainment,course_atr")
                        .build());

        if (effectiveStep != null) {
            progress.setCurrentStep(effectiveStep);
        }
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            progress.setCoordinatorEmail(effectiveEmail);
        }
        if (completedStepsList != null) {
            progress.setCompletedSteps(String.join(",", completedStepsList));
        }
        if (pendingStepsList != null) {
            progress.setPendingSteps(String.join(",", pendingStepsList));
        }
        progress.setUpdatedAt(ZonedDateTime.now());
        ccSetupProgressRepository.save(progress);
        return getCourseCoordinatorSetupProgress(effectiveEmail, targetMasterCourseId);
    }

    @Transactional
    public CourseCoordinatorSetupProgressDto completeCourseCoordinatorSetup(String coordinatorEmail, String masterCourseId) {
        return completeCourseCoordinatorSetup(coordinatorEmail, masterCourseId, null);
    }

    @Transactional
    public CourseCoordinatorSetupProgressDto completeCourseCoordinatorSetup(
            String coordinatorEmail,
            String masterCourseId,
            Map<String, Object> body) {
        String effectiveMasterCourseId = masterCourseId;
        String effectiveEmail = coordinatorEmail;

        if (body != null) {
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("masterCourseId")) {
                effectiveMasterCourseId = String.valueOf(body.get("masterCourseId"));
            }
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("offeringId")) {
                effectiveMasterCourseId = String.valueOf(body.get("offeringId"));
            }
            if ((effectiveMasterCourseId == null || effectiveMasterCourseId.isBlank()) && body.containsKey("programmeBatchCourseId")) {
                effectiveMasterCourseId = String.valueOf(body.get("programmeBatchCourseId"));
            }
            if ((effectiveEmail == null || effectiveEmail.isBlank()) && body.containsKey("coordinatorEmail")) {
                effectiveEmail = String.valueOf(body.get("coordinatorEmail"));
            }
            if ((effectiveEmail == null || effectiveEmail.isBlank()) && body.containsKey("email")) {
                effectiveEmail = String.valueOf(body.get("email"));
            }
        }

        if (effectiveMasterCourseId == null || effectiveMasterCourseId.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course ID is required to complete setup progress.");
        }
        String targetMasterCourseId = effectiveMasterCourseId.trim();

        System.out.println("[AcademicService] completeCourseCoordinatorSetup called | courseId: " + targetMasterCourseId);
        if (programmeBatchCourseRepository.existsById(targetMasterCourseId)) {
            enforceProgrammeBatchCourseScope(targetMasterCourseId);
            enforceCourseCoordinatorScope(targetMasterCourseId);
        }

        final String finalCompleteEmail = effectiveEmail;
        CourseCoordinatorSetupProgress progress = ccSetupProgressRepository.findByProgrammeBatchCourseId(targetMasterCourseId)
                .orElseGet(() -> CourseCoordinatorSetupProgress.builder()
                        .id("ccprog-" + UUID.randomUUID().toString().substring(0, 8))
                        .programmeBatchCourseId(targetMasterCourseId)
                        .coordinatorEmail(finalCompleteEmail)
                        .build());

        progress.setOverallStatus(SetupStepStatus.COMPLETED);
        progress.setCompletedSteps("cos,co_mapping,direct,indirect,attainment,course_atr");
        progress.setPendingSteps("");
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            progress.setCoordinatorEmail(effectiveEmail);
        }
        progress.setUpdatedAt(ZonedDateTime.now());
        ccSetupProgressRepository.save(progress);
        return getCourseCoordinatorSetupProgress(effectiveEmail, targetMasterCourseId);
    }

    // --- Programmes ---
    @Transactional(readOnly = true)
    public List<MasterProgramme> getAllProgrammes() {
        System.out.println("[AcademicService] getAllProgrammes called");
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isDirector()) {
            return getProgrammesBySchool(scope.getRequiredSchoolId());
        }
        if (scope != null && scope.isHod()) {
            List<Department> hodDepts = (scope.getEmail() != null && !scope.getEmail().isBlank())
                    ? departmentRepository.findByHodEmailIgnoreCase(scope.getEmail().trim())
                    : Collections.emptyList();
            if (hodDepts != null && !hodDepts.isEmpty()) {
                List<String> deptIds = hodDepts.stream().map(Department::getId).toList();
                List<MasterProgramme> list = masterProgrammeRepository.findByDepartmentIdInAndDeletedAtIsNull(deptIds);
                list.forEach(this::enrichProgrammeCoordinator);
                return list;
            }
            return getProgrammesByDepartment(scope.getRequiredDepartmentId());
        }
        if (scope != null && scope.isProgrammeCoordinator()) {
            Set<String> progIds = new LinkedHashSet<>();
            if (scope.getEmail() != null && !scope.getEmail().isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(scope.getEmail().trim());
                if (batches != null) {
                    batches.stream()
                            .map(ProgrammeBatch::getMasterProgrammeId)
                            .filter(id -> id != null && !id.isBlank())
                            .forEach(progIds::add);
                }
            }
            if (scope.getMasterProgrammeId() != null && !scope.getMasterProgrammeId().isBlank()) {
                progIds.add(scope.getMasterProgrammeId());
            }

            if (!progIds.isEmpty()) {
                List<MasterProgramme> progs = masterProgrammeRepository.findByIdInAndDeletedAtIsNull(progIds);
                progs.forEach(this::enrichProgrammeCoordinator);
                return progs;
            }

            if (scope.getMasterProgrammeId() != null) {
                MasterProgramme p = masterProgrammeRepository.findByIdAndDeletedAtIsNull(scope.getMasterProgrammeId()).orElse(null);
                if (p != null) {
                    enrichProgrammeCoordinator(p);
                    return List.of(p);
                }
            }
            return Collections.emptyList();
        }
        List<MasterProgramme> list = masterProgrammeRepository.findByDeletedAtIsNull();
        list.forEach(this::enrichProgrammeCoordinator);
        System.out.println("[AcademicService] Fetched all programmes (" + list.size() + " items)");
        return list;
    }

    @Transactional(readOnly = true)
    public MasterProgramme getProgrammeById(String id) {
        System.out.println("[AcademicService] getProgrammeById called | id: " + id);
        if (id == null || id.isBlank()) return null;
        MasterProgramme p = masterProgrammeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (p == null) return null;
        enforceProgrammeScope(p.getId());
        enrichProgrammeCoordinator(p);
        return p;
    }

    @Transactional(readOnly = true)
    public List<MasterProgramme> getProgrammesByCoordinatorEmail(String coordinatorEmail) {
        System.out.println("[AcademicService] getProgrammesByCoordinatorEmail called | coordinatorEmail: " + coordinatorEmail);
        CurrentUserScope scope = getScope();
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail.trim().toLowerCase()
                : (scope != null && scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null);

        Set<String> masterProgrammeIds = new LinkedHashSet<>();
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
            if (batches != null) {
                batches.stream()
                        .map(ProgrammeBatch::getMasterProgrammeId)
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(masterProgrammeIds::add);
            }
            userRepository.findByEmail(effectiveEmail).ifPresent(u -> {
                if (u.getMasterProgrammeId() != null && !u.getMasterProgrammeId().isBlank()) {
                    masterProgrammeIds.add(u.getMasterProgrammeId());
                }
            });
        }

        if (scope != null && scope.getMasterProgrammeId() != null && !scope.getMasterProgrammeId().isBlank()) {
            masterProgrammeIds.add(scope.getMasterProgrammeId());
        }

        if (!masterProgrammeIds.isEmpty()) {
            List<MasterProgramme> programmes = masterProgrammeRepository.findByIdInAndDeletedAtIsNull(masterProgrammeIds);
            programmes.forEach(this::enrichProgrammeCoordinator);
            System.out.println("[AcademicService] Found " + programmes.size() + " unique master-programmes for coordinatorEmail: " + effectiveEmail);
            return programmes;
        }

        List<MasterProgramme> all = getAllProgrammes();
        return all;
    }

    @Transactional(readOnly = true)
    public List<MasterProgramme> getProgrammesBySchool(String schoolId) {
        System.out.println("[AcademicService] getProgrammesBySchool called | schoolId: " + schoolId);
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isDirector()) {
            String dirSchoolId = scope.getRequiredSchoolId();
            if (schoolId != null && !schoolId.isBlank() && !schoolId.equals(dirSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You cannot view programmes of a different school.");
            }
            schoolId = dirSchoolId;
        }
        if (scope != null && scope.isHod()) {
            return getAllProgrammes();
        }
        if (scope != null && scope.isProgrammeCoordinator()) {
            enforceSchoolScope(schoolId);
            return getAllProgrammes();
        }
        List<Department> depts = departmentRepository.findBySchoolId(schoolId);
        if (depts == null || depts.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> deptIds = depts.stream().map(Department::getId).toList();
        List<MasterProgramme> list = masterProgrammeRepository.findByDepartmentIdInAndDeletedAtIsNull(deptIds);
        list.forEach(this::enrichProgrammeCoordinator);
        System.out.println("[AcademicService] Fetched programmes (" + list.size() + " items) for schoolId: " + schoolId);
        return list;
    }

    @Transactional(readOnly = true)
    public List<MasterProgramme> getProgrammesByDepartment(String departmentId) {
        System.out.println("[AcademicService] getProgrammesByDepartment called | departmentId: " + departmentId);
        CurrentUserScope scope = getScope();
        if (departmentId == null || departmentId.isBlank()) {
            return getAllProgrammes();
        }
        enforceDepartmentScope(departmentId);
        if (scope != null && scope.isProgrammeCoordinator()) {
            return getAllProgrammes();
        }
        List<MasterProgramme> list = masterProgrammeRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId);
        list.forEach(this::enrichProgrammeCoordinator);
        System.out.println("[AcademicService] Fetched programmes (" + list.size() + " items) for departmentId: " + departmentId);
        return list;
    }

    @Transactional
    public MasterProgramme saveProgramme(MasterProgramme programme) {
        System.out.println("[AcademicService] saveMasterProgramme called | id: " + (programme != null ? programme.getId() : "null") + " | name: " + (programme != null ? programme.getName() : "null") + " | coordinator: " + (programme != null ? programme.getCoordinator() : "null") + " | coordinatorEmail: " + (programme != null ? programme.getCoordinatorEmail() : "null"));
        if (programme == null) return null;

        if (programme.getId() != null) {
            MasterProgramme existing = masterProgrammeRepository.findByIdAndDeletedAtIsNull(programme.getId()).orElse(null);
            if (existing != null) {
                enforceProgrammeScope(existing.getId());
            }
        }
        if (programme.getDepartmentId() != null) {
            enforceDepartmentScope(programme.getDepartmentId());
        }

        MasterProgramme targetProg = programme;
        if (programme.getId() != null) {
            Optional<MasterProgramme> existingOpt = masterProgrammeRepository.findById(programme.getId());
            if (existingOpt.isPresent()) {
                MasterProgramme existing = existingOpt.get();
                existing.setDeletedAt(null);
                existing.setDeletedBy(null);
                if (programme.getName() != null) existing.setName(programme.getName());
                if (programme.getDegreeAwarded() != null) existing.setDegreeAwarded(programme.getDegreeAwarded());
                if (programme.getDepartmentId() != null) existing.setDepartmentId(programme.getDepartmentId());
                if (programme.getDurationYears() != null) existing.setDurationYears(programme.getDurationYears());
                if (programme.getStatus() != null) existing.setStatus(programme.getStatus());
                if (programme.getLevel() != null && !programme.getLevel().isBlank()) existing.setLevel(programme.getLevel().trim().toUpperCase());
                if (programme.getDepartmentName() != null) existing.setDepartmentName(programme.getDepartmentName());
                if (programme.getCoordinator() != null) existing.setCoordinator(programme.getCoordinator());
                if (programme.getCoordinatorEmail() != null) existing.setCoordinatorEmail(programme.getCoordinatorEmail());
                targetProg = existing;
            }
        } else {
            targetProg.setId("prog-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (targetProg.getLevel() == null || targetProg.getLevel().isBlank()) {
            targetProg.setLevel("UG");
        } else {
            targetProg.setLevel(targetProg.getLevel().trim().toUpperCase());
        }
        
        // Ensure department is loaded to get schoolId
        String deptId = targetProg.getDepartmentId();
        if (deptId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department ID is required.");
        }
        Department dept = departmentRepository.findById(deptId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Department ID."));
        String schoolId = dept.getSchoolId();
        
        String excludeId = targetProg.getId();
        
        if (targetProg.getName() != null) {
            boolean nameExists = masterProgrammeRepository.existsByNameInSchoolExcludeId(schoolId, targetProg.getName(), excludeId);
            if (nameExists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Programme name already exists in this school.");
            }
        }

        if (programme.getCoordinator() != null && !programme.getCoordinator().isBlank()) {
            targetProg.setCoordinator(programme.getCoordinator());
        }
        if (programme.getCoordinatorEmail() != null && !programme.getCoordinatorEmail().isBlank()) {
            targetProg.setCoordinatorEmail(programme.getCoordinatorEmail());
        }

        final MasterProgramme finalProg = targetProg;
        enrichProgrammeCoordinator(finalProg);

        // Populate department name if missing
        if ((finalProg.getDepartmentName() == null || finalProg.getDepartmentName().isBlank()) && finalProg.getDepartmentId() != null) {
            departmentRepository.findById(finalProg.getDepartmentId()).ifPresent(d -> finalProg.setDepartmentName(d.getName()));
        }

        // Bidirectionally synchronize user record if coordinator assigned
        String coordEmail = finalProg.getCoordinatorEmail();
        if (coordEmail != null && !coordEmail.isBlank()) {
            Optional<User> userOpt = userRepository.findByEmail(coordEmail.trim());
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByUsername(coordEmail.trim());
            }
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setMasterProgrammeId(finalProg.getId());
                user.setProgramme(finalProg.getName());
                if (finalProg.getDepartmentId() != null) {
                    user.setDepartmentId(finalProg.getDepartmentId());
                }
                if (user.getRole() == UserRole.FACULTY) {
                    user.setRole(UserRole.PROGRAMME_COORDINATOR);
                }
                userRepository.save(user);
                System.out.println("[AcademicService] Synchronized user " + user.getEmail() + " as PC for programme " + finalProg.getName());
            }
        }

        boolean isNewProg = (finalProg.getId() == null || !masterProgrammeRepository.existsByIdAndDeletedAtIsNull(finalProg.getId()));
        MasterProgramme saved = masterProgrammeRepository.save(finalProg);
        if (auditLogService != null) {
            auditLogService.recordSuccess(isNewProg ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE, com.dypiu.nba.audit.ResourceType.MASTER_PROGRAMME, saved.getId(), null, "ACTIVE", isNewProg ? "Created MasterProgramme" : "Updated MasterProgramme", java.util.Map.of("degreeAwarded", saved.getDegreeAwarded() != null ? saved.getDegreeAwarded() : "", "name", saved.getName() != null ? saved.getName() : ""));
        }
        System.out.println("[AcademicService] Saved programme with id: " + saved.getId() + ", coordinator: " + saved.getCoordinator() + ", coordinatorEmail: " + saved.getCoordinatorEmail());
        return saved;
    }

    @Transactional
    public void deleteProgramme(String id) {
        System.out.println("[AcademicService] deleteMasterProgramme called | id: " + id);
        enforceProgrammeScope(id);
        MasterProgramme existing = masterProgrammeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MasterProgramme not found: " + id));
        existing.setStatus("DELETED");
        existing.setDeletedAt(ZonedDateTime.now());
        
        CurrentUserScope scope = getScope();
        String deletedBy = (scope != null && scope.getEmail() != null) ? scope.getEmail() : "system";
        existing.setDeletedBy(deletedBy);
        
        masterProgrammeRepository.save(existing);
        
        // Soft delete all active child batches
        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(id);
        for (ProgrammeBatch batch : batches) {
            if (batch.getDeletedAt() == null) {
                batch.setStatus("DELETED");
                batch.setDeletedAt(ZonedDateTime.now());
                batch.setDeletedBy(deletedBy);
                programmeBatchRepository.save(batch);
                // Child courses of batches are handled by filtering on active batches in queries, or we can soft delete them.
                // Let's soft delete courses as well.
                List<ProgrammeBatchCourse> courses = programmeBatchCourseRepository.findByProgrammeBatchId(batch.getId());
                for (ProgrammeBatchCourse course : courses) {
                    if (course.getDeletedAt() == null) {
                        course.setStatus("DELETED");
                        course.setDeletedAt(ZonedDateTime.now());
                        course.setDeletedBy(deletedBy);
                        programmeBatchCourseRepository.save(course);
                    }
                }
            }
        }
        
        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    com.dypiu.nba.audit.AuditAction.DELETE,
                    com.dypiu.nba.audit.ResourceType.MASTER_PROGRAMME,
                    id,
                    null,
                    "DELETED",
                    "Soft-deleted MasterProgramme and its active batches/courses",
                    java.util.Map.of("degreeAwarded", existing.getDegreeAwarded() != null ? existing.getDegreeAwarded() : "")
            );
        }
        System.out.println("[AcademicService] Soft-deleted programme with id: " + id);
    }

    // --- Batches ---
    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getBatchesFiltered(
            String masterProgrammeId,
            String departmentId,
            String coordinatorEmail,
            String courseCoordinatorEmail,
            String hodEmail,
            String userEmail,
            String role,
            String status) {

        CurrentUserScope scope = getScope();

        // 1. Validate requested filters against the user's scope
        if (scope != null && !scope.isIqac()) {
            if (departmentId != null && !departmentId.isBlank()) {
                requestScopeAuthorizer.assertRequestedDepartment(departmentId);
            }
            if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
                requestScopeAuthorizer.assertRequestedProgramme(masterProgrammeId);
            }
            if (coordinatorEmail != null && !coordinatorEmail.isBlank()) {
                requestScopeAuthorizer.assertRequestedCoordinatorEmail(coordinatorEmail);
            }
            if (courseCoordinatorEmail != null && !courseCoordinatorEmail.isBlank()) {
                requestScopeAuthorizer.assertRequestedCourseCoordinatorEmail(courseCoordinatorEmail);
            }
            if (hodEmail != null && !hodEmail.isBlank()) {
                requestScopeAuthorizer.assertRequestedHodEmail(hodEmail);
            }
            if (userEmail != null && !userEmail.isBlank()) {
                requestScopeAuthorizer.assertRequestedUserEmail(userEmail, role);
            }
        }

        // 2. Resolve effective query parameters based on role scope
        final String requestedStatus = (status != null && !status.isBlank()) ? status.trim() : null;
        final boolean isAllStatus = "ALL".equalsIgnoreCase(requestedStatus) || "ANY".equalsIgnoreCase(requestedStatus);
        final String effectiveStatus = isAllStatus ? "ALL" : requestedStatus;

        String targetMasterProgrammeId = (masterProgrammeId != null && !masterProgrammeId.isBlank()) ? masterProgrammeId.trim() : null;
        String targetDepartmentId = (departmentId != null && !departmentId.isBlank()) ? departmentId.trim() : null;
        String targetCoordinatorEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank()) ? coordinatorEmail.trim().toLowerCase() : null;
        String targetCourseCoordinatorEmail = (courseCoordinatorEmail != null && !courseCoordinatorEmail.isBlank()) ? courseCoordinatorEmail.trim().toLowerCase() : null;

        // Apply role defaults when caller hasn't explicitly supplied them
        if (scope != null) {
            if (scope.isProgrammeCoordinator()) {
                targetCoordinatorEmail = scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null;
                if (targetMasterProgrammeId == null && scope.getMasterProgrammeId() != null && !scope.getMasterProgrammeId().isBlank()) {
                    targetMasterProgrammeId = scope.getMasterProgrammeId().trim();
                }
            } else if (scope.isHod()) {
                if (targetDepartmentId == null) {
                    if (scope.hasDepartmentScope()) {
                        targetDepartmentId = scope.getDepartmentId();
                    } else if (scope.getEmail() != null && !scope.getEmail().isBlank()) {
                        List<Department> hodDepts = departmentRepository.findByHodEmailIgnoreCase(scope.getEmail().trim());
                        if (hodDepts != null && !hodDepts.isEmpty()) {
                            List<String> deptIds = hodDepts.stream().map(Department::getId).toList();
                            List<ProgrammeBatch> batches = programmeBatchRepository.findBatchesFilteredByDepartmentIds(
                                    targetMasterProgrammeId, deptIds, targetCoordinatorEmail, effectiveStatus);
                            enrichBatchMetadata(batches);
                            return batches;
                        }
                    }
                }
            } else if (scope.isDirector()) {
                if (targetDepartmentId == null) {
                    List<Department> depts = departmentRepository.findBySchoolId(scope.getRequiredSchoolId());
                    List<String> deptIds = depts.stream().map(Department::getId).toList();
                    if (deptIds.isEmpty()) return Collections.emptyList();
                    List<ProgrammeBatch> batches = programmeBatchRepository.findBatchesFilteredByDepartmentIds(
                            targetMasterProgrammeId, deptIds, targetCoordinatorEmail, effectiveStatus);
                    enrichBatchMetadata(batches);
                    return batches;
                }
            } else if (scope.isFaculty()) {
                targetCourseCoordinatorEmail = scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null;
            }
        }

        // 3. Handle course coordinator / faculty filtering
        if (targetCourseCoordinatorEmail != null && !targetCourseCoordinatorEmail.isBlank()) {
            return getBatchesByCourseCoordinatorEmailAndFilters(
                    targetCourseCoordinatorEmail, targetMasterProgrammeId, targetDepartmentId, effectiveStatus);
        }

        // 4. Handle userEmail + role when provided by admin / global user
        if (userEmail != null && !userEmail.isBlank()) {
            String cleanUserEmail = userEmail.trim().toLowerCase();
            if ("PROGRAMME_COORDINATOR".equalsIgnoreCase(role) || "COORDINATOR".equalsIgnoreCase(role)) {
                targetCoordinatorEmail = cleanUserEmail;
            } else if ("COURSE_COORDINATOR".equalsIgnoreCase(role) || "FACULTY".equalsIgnoreCase(role)) {
                return getBatchesByCourseCoordinatorEmailAndFilters(
                    cleanUserEmail, targetMasterProgrammeId, targetDepartmentId, effectiveStatus);
            } else if ("HOD".equalsIgnoreCase(role)) {
                List<Department> hodDepts = departmentRepository.findByHodEmailIgnoreCase(cleanUserEmail);
                if (!hodDepts.isEmpty()) {
                    List<String> deptIds = hodDepts.stream().map(Department::getId).toList();
                    List<ProgrammeBatch> batches = programmeBatchRepository.findBatchesFilteredByDepartmentIds(
                            targetMasterProgrammeId, deptIds, targetCoordinatorEmail, effectiveStatus);
                    enrichBatchMetadata(batches);
                    return batches;
                }
            }
        }

        // 5. Execute unified single database query
        List<ProgrammeBatch> batches = programmeBatchRepository.findBatchesFiltered(
                targetMasterProgrammeId, targetDepartmentId, targetCoordinatorEmail, effectiveStatus);

        if (scope != null && scope.isProgrammeCoordinator()) {
            List<ProgrammeBatch> byCoordId = scope.getUserId() != null 
                    ? programmeBatchRepository.findByCoordinatorIdAndDeletedAtIsNull(scope.getUserId()) 
                    : Collections.emptyList();
            List<ProgrammeBatch> byEmail = scope.getEmail() != null && !scope.getEmail().isBlank()
                    ? programmeBatchRepository.findByCoordinatorEmailIgnoreCaseAndDeletedAtIsNull(scope.getEmail().trim())
                    : Collections.emptyList();
            List<ProgrammeBatch> combined = new ArrayList<>(batches);
            for (ProgrammeBatch b : byCoordId) {
                if (combined.stream().noneMatch(existing -> existing.getId().equals(b.getId()))) {
                    combined.add(b);
                }
            }
            for (ProgrammeBatch b : byEmail) {
                if (combined.stream().noneMatch(existing -> existing.getId().equals(b.getId()))) {
                    combined.add(b);
                }
            }
            final String filterProgId = targetMasterProgrammeId;
            batches = combined.stream().filter(b -> {
                if (filterProgId != null && !filterProgId.equalsIgnoreCase(b.getMasterProgrammeId())) {
                    return false;
                }
                if (requestedStatus == null) {
                    if ("INACTIVE".equalsIgnoreCase(b.getStatus())) {
                        return false;
                    }
                } else if (!isAllStatus) {
                    if (b.getStatus() != null && !requestedStatus.equalsIgnoreCase(b.getStatus())) {
                        return false;
                    }
                }
                if (scope.getUserId() != null && b.getCoordinatorId() != null && Objects.equals(b.getCoordinatorId(), scope.getUserId())) {
                    return true;
                }
                if (scope.getEmail() != null && !scope.getEmail().isBlank() && b.getCoordinatorEmail() != null && !b.getCoordinatorEmail().isBlank()) {
                    if (b.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim())) {
                        return true;
                    }
                }
                if (scope.getName() != null && !scope.getName().isBlank() && b.getCoordinatorName() != null && !b.getCoordinatorName().isBlank()) {
                    if (b.getCoordinatorName().trim().equalsIgnoreCase(scope.getName().trim())) {
                        return true;
                    }
                }
                if (scope.getMasterProgrammeId() != null && scope.getMasterProgrammeId().equalsIgnoreCase(b.getMasterProgrammeId())) {
                    return true;
                }
                if (b.getMasterProgrammeId() != null) {
                    MasterProgramme p = masterProgrammeRepository.findById(b.getMasterProgrammeId()).orElse(null);
                    if (p != null) {
                        if (scope.getEmail() != null && p.getCoordinatorEmail() != null && p.getCoordinatorEmail().trim().equalsIgnoreCase(scope.getEmail().trim())) {
                            return true;
                        }
                        if (scope.getName() != null && p.getCoordinator() != null && p.getCoordinator().trim().equalsIgnoreCase(scope.getName().trim())) {
                            return true;
                        }
                    }
                }
                return false;
            }).toList();
        }

        // 6. Enrich metadata in memory without N+1 queries
        enrichBatchMetadata(batches);
        return batches;
    }

    private void enrichBatchMetadata(List<ProgrammeBatch> batches) {
        if (batches == null || batches.isEmpty()) return;
        Set<String> progIds = batches.stream()
                .map(ProgrammeBatch::getMasterProgrammeId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (progIds.isEmpty()) return;
        Map<String, MasterProgramme> progMap = masterProgrammeRepository.findByIdInAndDeletedAtIsNull(progIds).stream()
                .collect(Collectors.toMap(MasterProgramme::getId, p -> p, (a, b) -> a));

        for (ProgrammeBatch batch : batches) {
            MasterProgramme prog = progMap.get(batch.getMasterProgrammeId());
            if (prog != null) {
                if (batch.getProgrammeName() == null || batch.getProgrammeName().isBlank()) {
                    batch.setProgrammeName(prog.getName());
                }
                if (batch.getProgrammeCode() == null || batch.getProgrammeCode().isBlank()) {
                    batch.setProgrammeCode(prog.getCode());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatchCourse> getProgrammeBatchCoursesByCoordinatorEmail(String coordinatorEmail, String programmeBatchId) {
        CurrentUserScope scope = getScope();
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail.trim().toLowerCase()
                : (scope != null && scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null);

        User user = null;
        if (effectiveEmail != null) {
            user = userRepository.findByEmailIgnoreCase(effectiveEmail)
                    .or(() -> userRepository.findByUsernameIgnoreCase(effectiveEmail))
                    .orElse(null);
        }

        Long userId = user != null ? user.getId() : (scope != null ? scope.getUserId() : null);
        String userName = user != null ? user.getName() : (scope != null ? scope.getName() : null);

        List<ProgrammeBatchCourse> list;
        try {
            list = (programmeBatchId != null && !programmeBatchId.isBlank())
                    ? programmeBatchCourseRepository.findByProgrammeBatchId(programmeBatchId.trim())
                    : programmeBatchCourseRepository.findAll();
        } catch (Exception e) {
            System.err.println("[AcademicService] Error querying programme batch courses for batch " + programmeBatchId + ": " + e.getMessage());
            return Collections.emptyList();
        }

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        boolean requireApprovalCheck = (scope == null || scope.isFaculty());

        List<ProgrammeBatchCourse> filtered = list.stream()
                .filter(o -> {
                    if (requireApprovalCheck && !isCourseAllocationApproved(o)) {
                        return false;
                    }
                    if (userId != null && o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), userId)) {
                        return true;
                    }
                    if (userName != null && o.getCourseCoordinatorName() != null && o.getCourseCoordinatorName().equalsIgnoreCase(userName)) {
                        return true;
                    }
                    if (effectiveEmail != null && o.getAssignedFaculty() != null && o.getAssignedFaculty().toLowerCase().contains(effectiveEmail)) {
                        return true;
                    }
                    if (userName != null && o.getAssignedFaculty() != null && o.getAssignedFaculty().toLowerCase().contains(userName.toLowerCase())) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        filtered.forEach(this::enrichOffering);
        return filtered;
    }

    private List<ProgrammeBatch> getBatchesByCourseCoordinatorEmailAndFilters(
            String courseCoordinatorEmail, String masterProgrammeId, String departmentId, String status) {
        List<ProgrammeBatchCourse> assignedCourses = getProgrammeBatchCoursesByCoordinatorEmail(courseCoordinatorEmail, null);
        if (assignedCourses.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> programmeBatchIds = assignedCourses.stream()
                .map(ProgrammeBatchCourse::getProgrammeBatchId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (programmeBatchIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProgrammeBatch> batches = programmeBatchRepository.findAllById(programmeBatchIds).stream()
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> status == null || (b.getStatus() != null && b.getStatus().equalsIgnoreCase(status)))
                .filter(b -> masterProgrammeId == null || masterProgrammeId.equals(b.getMasterProgrammeId()))
                .collect(Collectors.toList());

        if (departmentId != null && !departmentId.isBlank()) {
            Set<String> progIds = batches.stream().map(ProgrammeBatch::getMasterProgrammeId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!progIds.isEmpty()) {
                Map<String, MasterProgramme> progMap = masterProgrammeRepository.findByIdInAndDeletedAtIsNull(progIds).stream()
                        .collect(Collectors.toMap(MasterProgramme::getId, p -> p, (a, b) -> a));
                batches = batches.stream()
                        .filter(b -> {
                            MasterProgramme mp = progMap.get(b.getMasterProgrammeId());
                            return mp != null && departmentId.equals(mp.getDepartmentId());
                        })
                        .collect(Collectors.toList());
            }
        }

        enrichBatchMetadata(batches);
        return batches;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getAllBatches() {
        return getBatchesFiltered(null, null, null, null, null, null, null, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getBatchesByCoordinatorEmailAndProgramme(String coordinatorEmail, String masterProgrammeId) {
        return getBatchesFiltered(masterProgrammeId, null, coordinatorEmail, null, null, null, null, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getBatchesByCourseCoordinatorEmail(String courseCoordinatorEmail) {
        return getBatchesFiltered(null, null, null, courseCoordinatorEmail, null, null, null, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getBatchesByProgramme(String masterProgrammeId) {
        return getBatchesFiltered(masterProgrammeId, null, null, null, null, null, null, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public ProgrammeBatch getBatchById(String id) {
        if (id == null || id.isBlank()) return null;
        ProgrammeBatch batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(id.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("ProgrammeBatch not found with id: " + id));
        enforceBatchScope(batch.getId());
        if (batch.getMasterProgrammeId() != null) {
            masterProgrammeRepository.findByIdAndDeletedAtIsNull(batch.getMasterProgrammeId()).ifPresent(p -> {
                if (batch.getProgrammeName() == null || batch.getProgrammeName().isBlank()) {
                    batch.setProgrammeName(p.getName());
                }
                if (batch.getProgrammeCode() == null || batch.getProgrammeCode().isBlank()) {
                    batch.setProgrammeCode(p.getCode());
                }
            });
        }
        return batch;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatch> getBatchesScoped(String masterProgrammeId, String userEmail, String role) {
        return getBatchesFiltered(masterProgrammeId, null, null, null, null, userEmail, role, "ACTIVE");
    }

    @Transactional
    public ProgrammeBatch saveBatch(ProgrammeBatch batch) {
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ProgrammeBatch details cannot be null.");
        }
        if (batch.getId() != null) {
            ProgrammeBatch existing = programmeBatchRepository.findById(batch.getId()).orElse(null);
            if (existing != null && existing.getDeletedAt() == null) {
                enforceProgrammeScope(existing.getMasterProgrammeId());
                batchLifecycleService.enforceBatchEditability(batch.getId());
            }
        }
        if (batch.getMasterProgrammeId() != null) {
            enforceProgrammeScope(batch.getMasterProgrammeId());
        }
        if (batch.getStartYear() != null && batch.getEndYear() != null) {
            if (batch.getEndYear() <= batch.getStartYear()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endYear (" + batch.getEndYear() + ") must be greater than startYear (" + batch.getStartYear() + ").");
            }
            batch.setDurationYears(batch.getEndYear() - batch.getStartYear());
        }

        // Active start_year uniqueness check
        if (batch.getMasterProgrammeId() != null && batch.getStartYear() != null) {
            String currentId = batch.getId();
            boolean conflictExists = (currentId != null && !currentId.isBlank())
                    ? programmeBatchRepository.existsByMasterProgrammeIdAndStartYearAndIdNotAndDeletedAtIsNull(batch.getMasterProgrammeId(), batch.getStartYear(), currentId)
                    : programmeBatchRepository.existsByMasterProgrammeIdAndStartYearAndDeletedAtIsNull(batch.getMasterProgrammeId(), batch.getStartYear());
            if (conflictExists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A batch for start year " + batch.getStartYear() + " already exists in this programme.");
            }
        }

        ProgrammeBatch targetBatch = batch;
        boolean isNewBatch = true;

        if (batch.getId() != null && !batch.getId().isBlank()) {
            Optional<ProgrammeBatch> byIdOpt = programmeBatchRepository.findById(batch.getId());
            if (byIdOpt.isPresent()) {
                targetBatch = byIdOpt.get();
                isNewBatch = false;
            }
        } else if (batch.getMasterProgrammeId() != null && batch.getStartYear() != null) {
            Optional<ProgrammeBatch> softDeletedOpt = programmeBatchRepository.findFirstByMasterProgrammeIdAndStartYear(batch.getMasterProgrammeId(), batch.getStartYear());
            if (softDeletedOpt.isPresent() && softDeletedOpt.get().getDeletedAt() != null) {
                targetBatch = softDeletedOpt.get();
                isNewBatch = false;
            }
        }

        if (isNewBatch && (targetBatch.getId() == null || targetBatch.getId().isBlank())) {
            targetBatch.setId("batch-" + UUID.randomUUID().toString().substring(0, 8));
        }

        targetBatch.setDeletedAt(null);
        targetBatch.setDeletedBy(null);
        targetBatch.setStatus(batch.getStatus() != null ? batch.getStatus() : "ACTIVE");
        if (batch.getMasterProgrammeId() != null) targetBatch.setMasterProgrammeId(batch.getMasterProgrammeId());
        if (batch.getName() != null) targetBatch.setName(batch.getName());
        if (batch.getStartYear() != null) targetBatch.setStartYear(batch.getStartYear());
        if (batch.getEndYear() != null) targetBatch.setEndYear(batch.getEndYear());
        if (batch.getDurationYears() != null) targetBatch.setDurationYears(batch.getDurationYears());
        if (batch.getCoordinatorId() != null) targetBatch.setCoordinatorId(batch.getCoordinatorId());
        if (batch.getCoordinatorName() != null) targetBatch.setCoordinatorName(batch.getCoordinatorName());
        if (batch.getCoordinatorEmail() != null) targetBatch.setCoordinatorEmail(batch.getCoordinatorEmail());
        if (batch.getYearLevel() != null) targetBatch.setYearLevel(batch.getYearLevel());
        if (batch.getProgrammeName() != null) targetBatch.setProgrammeName(batch.getProgrammeName());
        if (batch.getProgrammeCode() != null) targetBatch.setProgrammeCode(batch.getProgrammeCode());

        ProgrammeBatch saved = programmeBatchRepository.save(targetBatch);
        if (auditLogService != null) {
            auditLogService.recordSuccess(isNewBatch ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE, com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH, saved.getId(), null, "ACTIVE", isNewBatch ? "Created ProgrammeBatch" : "Updated ProgrammeBatch", java.util.Map.of("name", saved.getName() != null ? saved.getName() : ""));
        }
        return saved;
    }

    @Transactional
    public void deleteBatch(String id) {
        ProgrammeBatch batch = programmeBatchRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProgrammeBatch not found with id: " + id));
        enforceProgrammeScope(batch.getMasterProgrammeId());
        CurrentUserScope scope = getScope();
        batch.setDeletedAt(ZonedDateTime.now());
        batch.setDeletedBy(scope != null ? (scope.getEmail() != null ? scope.getEmail() : scope.getUsername()) : "SYSTEM");
        programmeBatchRepository.save(batch);
    }

    // --- Courses ---
    @Transactional(readOnly = true)
    public List<ProgrammeBatchCourse> getAllCourses() {
        System.out.println("[AcademicService] getAllCourses called");
        CurrentUserScope scope = getScope();
        List<ProgrammeBatchCourse> all = programmeBatchCourseRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null)
                .map(this::enrichOffering)
                .toList();
        if (scope == null || scope.isIqac()) {
            return all;
        }
        if (scope.isDirector()) {
            List<Department> depts = departmentRepository.findBySchoolId(scope.getRequiredSchoolId());
            List<String> deptIds = depts.stream().map(Department::getId).toList();
            List<MasterProgramme> progs = deptIds.isEmpty() ? Collections.emptyList() : masterProgrammeRepository.findByDepartmentIdInAndDeletedAtIsNull(deptIds);
            List<String> progIds = progs.stream().map(MasterProgramme::getId).toList();
            List<ProgrammeBatch> batches = progIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(progIds);
            Set<String> batchIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            return all.stream().filter(c -> batchIds.contains(c.getProgrammeBatchId())).toList();
        }
        if (scope.isHod() || scope.isProgrammeCoordinator()) {
            List<MasterProgramme> progs = getAllProgrammes();
            List<String> progIds = progs.stream().map(MasterProgramme::getId).toList();
            List<ProgrammeBatch> batches = progIds.isEmpty() ? Collections.emptyList() : programmeBatchRepository.findByMasterProgrammeIdIn(progIds);
            Set<String> batchIds = batches.stream().map(ProgrammeBatch::getId).collect(Collectors.toSet());
            return all.stream().filter(c -> batchIds.contains(c.getProgrammeBatchId())).toList();
        }
        if (scope.isFaculty()) {
            return all.stream().filter(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                boolean isAssigned = isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
                return isAssigned && isCourseAllocationApproved(o);
            }).toList();
        }
        return all;
    }

    @Transactional(readOnly = true)
    public ProgrammeBatchCourse getCourseById(String id) {
        System.out.println("[AcademicService] getCourseById called | id: " + id);
        ProgrammeBatchCourse course = programmeBatchCourseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + id));
        enforceProgrammeBatchCourseScope(id);
        return enrichOffering(course);
    }

    @Transactional(readOnly = true)
    public List<ProgrammeBatchCourse> getCoursesByProgramme(String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AcademicService] getCoursesByMasterProgramme called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);
        enforceProgrammeScope(masterProgrammeId);
        List<ProgrammeBatchCourse> list;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            list = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(programmeBatchId);
        } else {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);
            List<String> batchIds = batches.stream().map(ProgrammeBatch::getId).toList();
            list = batchIds.isEmpty() ? Collections.emptyList() : programmeBatchCourseRepository.findByProgrammeBatchIdInAndDeletedAtIsNull(batchIds);
        }
        CurrentUserScope scope = getScope();

        if (scope != null && scope.isFaculty()) {
            list = list.stream().filter(o -> {
                boolean isCoord = (o.getCourseCoordinatorId() != null && Objects.equals(o.getCourseCoordinatorId(), scope.getUserId()));
                boolean isAssigned = isCoord || (o.getAssignedFaculty() != null && (o.getAssignedFaculty().contains(scope.getEmail()) || o.getAssignedFaculty().contains(scope.getName())));
                return isAssigned && isCourseAllocationApproved(o);
            }).collect(Collectors.toList());
        }
        return list.stream().map(this::enrichOffering).collect(Collectors.toList());
    }

    @Transactional
    public ProgrammeBatchCourse saveCourse(ProgrammeBatchCourse course) {
        System.out.println("[AcademicService] saveCourse called | name: " + (course != null ? course.getName() : "null"));
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course details cannot be null.");
        }
        if (course.getProgrammeBatchId() != null) {
            enforceBatchScope(course.getProgrammeBatchId());
        }
        if (course.getId() != null) {
            enforceProgrammeBatchCourseScope(course.getId());
        }
        if (course.getId() == null) course.setId("offering-" + UUID.randomUUID().toString().substring(0, 8));
        if (course.getSemester() == null) course.setSemester(1);
        boolean isNewCourse = !programmeBatchCourseRepository.existsById(course.getId());
        ProgrammeBatchCourse saved = programmeBatchCourseRepository.save(course);
        if (auditLogService != null) {
            auditLogService.recordSuccess(isNewCourse ? com.dypiu.nba.audit.AuditAction.CREATE : com.dypiu.nba.audit.AuditAction.UPDATE, com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH_COURSE, saved.getId(), null, "ACTIVE", isNewCourse ? "Created Course" : "Updated Course", java.util.Map.of("code", saved.getCode() != null ? saved.getCode() : "", "name", saved.getName() != null ? saved.getName() : ""));
        }
        System.out.println("[AcademicService] Saved course with id: " + saved.getId());
        return enrichOffering(saved);
    }

    @Transactional
    public void deleteCourse(String id) {
        System.out.println("[AcademicService] deleteCourse called | id: " + id);
        deleteProgrammeBatchCourse(id);
    }

    // --- Students ---
    @Transactional(readOnly = true)
    public List<Student> getStudentsByBatch(String programmeBatchId) {
        System.out.println("[AcademicService] getStudentsByProgrammeBatch called | programmeBatchId: " + programmeBatchId);
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            enforceBatchScope(programmeBatchId);
        }
        List<Student> list = studentRepository.findByProgrammeBatchId(programmeBatchId);
        System.out.println("[AcademicService] Fetched students (" + list.size() + " items) for programmeBatchId: " + programmeBatchId);
        return list;
    }

    @Transactional
    public Student saveStudent(Student student) {
        System.out.println("[AcademicService] saveStudent called | name: " + (student != null ? student.getName() : "null"));
        if (student == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student details cannot be null.");
        }
        if (student.getProgrammeBatchId() != null) {
            enforceBatchScope(student.getProgrammeBatchId());
        }
        if (student.getId() != null) {
            Student existing = studentRepository.findById(student.getId()).orElse(null);
            if (existing != null && existing.getProgrammeBatchId() != null) {
                enforceBatchScope(existing.getProgrammeBatchId());
            }
        }
        if (student.getId() == null) student.setId("std-" + UUID.randomUUID().toString().substring(0, 8));
        Student saved = studentRepository.save(student);
        System.out.println("[AcademicService] Saved student with id: " + saved.getId());
        return saved;
    }

    @Transactional
    public void deleteStudent(String id) {
        System.out.println("[AcademicService] deleteStudent called | id: " + id);
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
        if (student.getProgrammeBatchId() != null) {
            enforceBatchScope(student.getProgrammeBatchId());
        }
        studentRepository.deleteById(id);
        System.out.println("[AcademicService] Deleted student with id: " + id);
    }

    // --- HOD Department Summary ---
    @Transactional(readOnly = true)
    public HodDepartmentSummaryDto getHodDepartmentSummary(String hodEmail) {
        return getHodDepartmentSummary(null, hodEmail);
    }

    @Transactional(readOnly = true)
    public HodDepartmentSummaryDto getHodDepartmentSummary(String departmentId, String hodEmail) {
        System.out.println("[AcademicService] getHodDepartmentSummary called | departmentId: " + departmentId + " | hodEmail: " + hodEmail);
        CurrentUserScope scope = getScope();
        if (departmentId != null && !departmentId.isBlank() && !departmentId.equals("dept-1")) {
            enforceDepartmentScope(departmentId.trim());
        }

        String targetDeptId = null;

        if (scope != null && scope.isHod()) {
            targetDeptId = scope.getRequiredDepartmentId();
        } else if (departmentId != null && !departmentId.isBlank()) {
            targetDeptId = departmentId.trim();
        } else if (hodEmail != null && !hodEmail.isBlank()) {
            List<Department> deptList = departmentRepository.findByHodEmailIgnoreCase(hodEmail.trim());
            if (!deptList.isEmpty()) {
                targetDeptId = deptList.get(0).getId();
            }
        } else if (scope != null && scope.getDepartmentId() != null) {
            targetDeptId = scope.getDepartmentId();
        }

        if (targetDeptId == null || targetDeptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department scope cannot be determined.");
        }

        enforceDepartmentScope(targetDeptId);

        final String finalDeptId = targetDeptId;
        Department dept = departmentRepository.findById(finalDeptId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + finalDeptId));

        enforceSchoolScope(dept.getSchoolId());

        String deptId = dept.getId();
        String deptName = dept.getName();
        String deptCode = dept.getCode();
        String resolvedHodName = dept.getHod();
        String resolvedHodEmail = (dept.getHodEmail() != null && !dept.getHodEmail().isBlank()) ? dept.getHodEmail() : hodEmail;
        String schoolId = dept.getSchoolId();

        // School info
        String schoolName = "School of Engineering and Technology";
        if (schoolId != null) {
            Optional<School> schOpt = schoolRepository.findById(schoolId);
            if (schOpt.isPresent()) {
                schoolName = schOpt.get().getName();
            }
        }

        // Programmes under department
        List<MasterProgramme> programmes = masterProgrammeRepository.findByDepartmentIdAndDeletedAtIsNull(deptId);
        int programmeCount = programmes.size();

        // Count assigned coordinators
        int assignedCoordinatorsCount = (int) programmes.stream()
                .filter(p -> (p.getCoordinator() != null && !p.getCoordinator().isBlank() && !"Unassigned".equalsIgnoreCase(p.getCoordinator()) && !"No coordinator assigned yet".equalsIgnoreCase(p.getCoordinator()) && !"Pending HOD Assignment".equalsIgnoreCase(p.getCoordinator())) || (p.getCoordinatorEmail() != null && !p.getCoordinatorEmail().isBlank()))
                .count();

        // Courses under department's programmes
        List<String> progIds = programmes.stream().map(MasterProgramme::getId).toList();
        int courseCount = 0;
        if (!progIds.isEmpty()) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeIdIn(progIds);
            List<String> batchIds = batches.stream().map(ProgrammeBatch::getId).toList();
            courseCount = batchIds.isEmpty() ? 0 : programmeBatchCourseRepository.findByProgrammeBatchIdInAndDeletedAtIsNull(batchIds).size();
        }

        HodSetupProgressDto progressDto = getHodSetupProgress(deptId, resolvedHodEmail);
        System.out.println("[AcademicService] Fetched HOD department summary for deptId: " + deptId + " (" + deptName + ") | hodEmail: " + resolvedHodEmail);

        return HodDepartmentSummaryDto.builder()
                .deptId(deptId)
                .deptCode(deptCode)
                .deptName(deptName)
                .hodName(resolvedHodName)
                .hodEmail(resolvedHodEmail)
                .schoolId(schoolId)
                .schoolName(schoolName)
                .programmeCount(programmeCount)
                .assignedCoordinatorsCount(assignedCoordinatorsCount)
                .courseCount(courseCount)
                .setupProgress(progressDto)
                .build();
    }

    private String resolveTargetDeptId(String departmentId, String hodEmail) {
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isHod()) {
            return scope.getRequiredDepartmentId();
        }
        if (departmentId != null && !departmentId.isBlank() && !departmentId.contains("@") && !departmentId.equals("null")) {
            return departmentId;
        }

        String search = (hodEmail != null && !hodEmail.isBlank() && !hodEmail.equals("null"))
                ? hodEmail.trim()
                : (departmentId != null && !departmentId.equals("null") ? departmentId.trim() : null);

        if (search != null && !search.isBlank()) {
            List<Department> deptList = departmentRepository.findByHodEmailIgnoreCase(search);
            if (!deptList.isEmpty()) {
                return deptList.get(0).getId();
            }
        }
        if (scope != null && scope.getDepartmentId() != null) {
            return scope.getDepartmentId();
        }
        return departmentId;
    }

    // --- HOD Setup Progress ---
    @Transactional(readOnly = true)
    public HodSetupProgressDto getHodSetupProgress(
            String departmentId,
            String hodEmail) {
        System.out.println("[AcademicService] getHodSetupProgress called | departmentId: " + departmentId + " | hodEmail: " + hodEmail);
        if (departmentId != null && !departmentId.isBlank() && !departmentId.equals("dept-1")) {
            enforceDepartmentScope(departmentId.trim());
        }

        String targetDeptId = resolveTargetDeptId(departmentId, hodEmail);
        if (targetDeptId == null || targetDeptId.isBlank()) {
            return null;
        }
        enforceDepartmentScope(targetDeptId);

        final String finalDeptId = targetDeptId;
        HodSetupProgress progress = hodSetupProgressRepository
                .findByDepartmentId(finalDeptId)
                .orElseGet(() -> createDefaultProgress(finalDeptId, hodEmail));

        return buildHodSetupProgressDto(progress);
    }

    @Transactional
    public HodSetupProgressDto updateHodSetupProgress(
            String departmentId,
            Integer stepNumber,
            String hodEmail) {
        return updateHodSetupProgress(departmentId, stepNumber, null, null, hodEmail);
    }

    @Transactional
    public HodSetupProgressDto updateHodSetupProgress(
            String departmentId,
            Integer targetStep,
            String completedStep,
            List<String> completedStepsList,
            String hodEmail) {
        System.out.println("[AcademicService] updateHodSetupProgress called | departmentId: " + departmentId + " | targetStep: " + targetStep + " | completedStep: " + completedStep + " | completedStepsList: " + completedStepsList + " | hodEmail: " + hodEmail);
        if (departmentId != null && !departmentId.isBlank() && !departmentId.equals("dept-1")) {
            enforceDepartmentScope(departmentId.trim());
        }

        String targetDeptId = resolveTargetDeptId(departmentId, hodEmail);
        if (targetDeptId == null || targetDeptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department scope cannot be determined.");
        }
        enforceDepartmentScope(targetDeptId);

        final String finalDeptId = targetDeptId;
        HodSetupProgress progress = hodSetupProgressRepository
                .findByDepartmentId(finalDeptId)
                .orElseGet(() -> createDefaultProgress(finalDeptId, hodEmail));

        Set<String> existingCompleted = new LinkedHashSet<>();
        if (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank()) {
            for (String s : progress.getCompletedSteps().split(",")) {
                String norm = normalizeHodStepName(s);
                if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
            }
        }

        // Add newly completed step(s)
        if (completedStepsList != null && !completedStepsList.isEmpty()) {
            for (Object item : completedStepsList) {
                String norm = normalizeHodStepName(item);
                if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
            }
        } else if (completedStep != null && !completedStep.isBlank()) {
            String norm = normalizeHodStepName(completedStep);
            if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
        } else if (targetStep != null) {
            String norm = normalizeHodStepName(targetStep);
            if (norm != null && !norm.isBlank()) existingCompleted.add(norm);
        }

        List<String> ALL_STEPS = List.of("master_courses", "batch", "coordinators", "outcomes", "review");
        List<String> completed = ALL_STEPS.stream().filter(existingCompleted::contains).toList();
        List<String> pending = ALL_STEPS.stream().filter(s -> !existingCompleted.contains(s)).toList();

        int currentStep;
        if (completed.size() == ALL_STEPS.size()) {
            currentStep = 1;
        } else if (targetStep != null && targetStep >= 1 && targetStep <= 5) {
            currentStep = (targetStep < 5 ? targetStep + 1 : 1);
        } else {
            currentStep = progress.getCurrentStep() != null ? progress.getCurrentStep() : 1;
        }

        SetupStepStatus overallStatus;
        if (completed.size() == ALL_STEPS.size()) {
            overallStatus = SetupStepStatus.COMPLETED;
        } else if (completed.isEmpty()) {
            overallStatus = SetupStepStatus.NOT_STARTED;
        } else {
            overallStatus = SetupStepStatus.IN_PROGRESS;
        }

        progress.setCurrentStep(currentStep);
        progress.setOverallStatus(overallStatus);
        progress.setCompletedSteps(String.join(",", completed));
        progress.setPendingSteps(String.join(",", pending));
        progress.setUpdatedAt(ZonedDateTime.now());

        if (hodEmail != null && !hodEmail.isBlank()) {
            progress.setHodEmail(hodEmail.trim());
        }

        hodSetupProgressRepository.save(progress);

        System.out.println("[AcademicService] HOD setup progress updated | targetDeptId=" + targetDeptId + " | currentStep=" + currentStep + " | completed=" + completed + " | pending=" + pending);

        return buildHodSetupProgressDto(progress);
    }

    private String normalizeHodStepName(Object step) {
        if (step == null) return null;
        String s = String.valueOf(step).trim().toLowerCase();
        return switch (s) {
            case "1", "master_course", "master_courses", "course", "courses", "mastercourse" -> "master_courses";
            case "2", "batch", "batches", "batch_setup" -> "batch";
            case "3", "coordinator", "coordinators", "coordinator_allocation", "allocation", "programme_coordinator", "programme_coordinators" -> "coordinators";
            case "4", "outcome", "outcomes", "po_pso", "po_pso_peo", "pos", "peo", "peos" -> "outcomes";
            case "5", "review", "confirm", "review_confirm", "review_and_confirm" -> "review";
            default -> s;
        };
    }

    private HodSetupProgress createDefaultProgress(
            String departmentId,
            String hodEmail) {

        return HodSetupProgress.builder()
                .id("progress-dept-" + departmentId)
                .departmentId(departmentId)
                .hodEmail(hodEmail)
                .currentStep(1)
                .overallStatus(SetupStepStatus.IN_PROGRESS)
                .completedSteps("")
                .pendingSteps("master_courses,batch,coordinators,outcomes,review")
                .build();
    }

    private void validateDepartmentId(String departmentId) {

        if (departmentId == null || departmentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Department ID is required"
            );
        }
    }

    private void validateStepNumber(Integer stepNumber) {

        if (stepNumber == null || stepNumber < 1 || stepNumber > 5) {
            throw new IllegalArgumentException(
                    "Step number must be between 1 and 5"
            );
        }
    }

    private HodSetupProgressDto buildHodSetupProgressDto(
            HodSetupProgress progress) {

        List<String> completedList =
                progress.getCompletedSteps() != null
                        && !progress.getCompletedSteps().isBlank()
                        ? Arrays.asList(
                        progress.getCompletedSteps().split(",")
                )
                        : Collections.emptyList();

        List<String> pendingList =
                progress.getPendingSteps() != null
                        && !progress.getPendingSteps().isBlank()
                        ? Arrays.asList(
                        progress.getPendingSteps().split(",")
                )
                        : Collections.emptyList();

        return HodSetupProgressDto.builder()
                .id(progress.getId())
                .departmentId(progress.getDepartmentId())
                .hodEmail(progress.getHodEmail())
                .currentStep(progress.getCurrentStep())
                .overallStatus(progress.getOverallStatus())
                .completedSteps(completedList)
                .pendingSteps(pendingList)
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    @Transactional
    public HodSetupProgressDto completeHodSetup(
            String departmentId,
            String hodEmail) {
        System.out.println("[AcademicService] completeHodSetup called | departmentId: " + departmentId + " | hodEmail: " + hodEmail);

        String targetDeptId = resolveTargetDeptId(departmentId, hodEmail);
        validateDepartmentId(targetDeptId);
        enforceDepartmentScope(targetDeptId);

        HodSetupProgress progress = hodSetupProgressRepository
                .findByDepartmentId(targetDeptId)
                .orElseGet(() -> createDefaultProgress(
                        targetDeptId,
                        hodEmail
                ));

        progress.setCurrentStep(1);

        progress.setCompletedSteps(
                "master_courses,batch,coordinators,outcomes,review"
        );

        progress.setPendingSteps("");

        progress.setOverallStatus(
                SetupStepStatus.COMPLETED
        );

        progress.setUpdatedAt(ZonedDateTime.now());

        if (hodEmail != null && !hodEmail.isBlank()) {
            progress.setHodEmail(hodEmail.trim());
        }

        hodSetupProgressRepository.save(progress);

        System.out.println("[AcademicService] HOD setup marked as COMPLETED for targetDeptId: " + targetDeptId);

        return buildHodSetupProgressDto(progress);
    }

    private String resolveTargetProgId(String masterProgrammeId, String coordinatorEmail) {
        CurrentUserScope scope = getScope();
        String effectiveEmail = (coordinatorEmail != null && !coordinatorEmail.isBlank())
                ? coordinatorEmail.trim().toLowerCase()
                : (scope != null && scope.getEmail() != null ? scope.getEmail().trim().toLowerCase() : null);

        if (scope != null && scope.isProgrammeCoordinator()) {
            if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
                enforceProgrammeScope(masterProgrammeId.trim());
                return masterProgrammeId.trim();
            }
            if (scope.getMasterProgrammeId() != null && !scope.getMasterProgrammeId().isBlank()) {
                return scope.getMasterProgrammeId().trim();
            }
            if (effectiveEmail != null && !effectiveEmail.isBlank()) {
                List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
                if (batches != null && !batches.isEmpty()) {
                    return batches.get(0).getMasterProgrammeId();
                }
            }
            return scope.getMasterProgrammeId();
        }

        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            return masterProgrammeId.trim();
        }
        if (effectiveEmail != null && !effectiveEmail.isBlank()) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByCoordinatorEmailIgnoreCase(effectiveEmail);
            if (batches != null && !batches.isEmpty()) {
                return batches.get(0).getMasterProgrammeId();
            }
            List<MasterProgramme> list = masterProgrammeRepository.findByDeletedAtIsNull();
            MasterProgramme p = list.stream().filter(pr -> (pr.getCoordinatorEmail() != null && effectiveEmail.equalsIgnoreCase(pr.getCoordinatorEmail().trim())) || (pr.getCoordinator() != null && effectiveEmail.equalsIgnoreCase(pr.getCoordinator().trim()))).findFirst().orElse(null);
            if (p != null) return p.getId();
        }
        if (scope != null && scope.getMasterProgrammeId() != null) {
            return scope.getMasterProgrammeId();
        }
        return null;
    }

    // --- MasterProgramme Coordinator Summary & Setup Progress ---
    @Transactional(readOnly = true)
    public ProgrammeCoordinatorSummaryDto getProgrammeCoordinatorSummary(String coordinatorEmail, String masterProgrammeId) {
        System.out.println("[AcademicService] getProgrammeCoordinatorSummary called | coordinatorEmail: " + coordinatorEmail + " | masterProgrammeId: " + masterProgrammeId);

        String targetProgId = resolveTargetProgId(masterProgrammeId, coordinatorEmail);
        if (targetProgId == null || targetProgId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MasterProgramme scope cannot be determined.");
        }
        enforceProgrammeScope(targetProgId);

        MasterProgramme prog = masterProgrammeRepository.findByIdAndDeletedAtIsNull(targetProgId)
                .orElseThrow(() -> new ResourceNotFoundException("MasterProgramme not found: " + targetProgId));
        enrichProgrammeCoordinator(prog);

        List<MasterProgramme> assignedProgrammes = List.of(prog);
        CurrentUserScope scope = getScope();
        if (scope != null && scope.isIqac()) {
            assignedProgrammes = masterProgrammeRepository.findByDeletedAtIsNull();
            assignedProgrammes.forEach(this::enrichProgrammeCoordinator);
        } else if (scope != null && scope.isHod()) {
            assignedProgrammes = getAllProgrammes();
            assignedProgrammes.forEach(this::enrichProgrammeCoordinator);
        } else if (scope != null && scope.isDirector()) {
            assignedProgrammes = getProgrammesBySchool(scope.getRequiredSchoolId());
            assignedProgrammes.forEach(this::enrichProgrammeCoordinator);
        }

        String resolvedName = "MasterProgramme Coordinator";
        String resolvedEmail = coordinatorEmail != null ? coordinatorEmail : "";

        if (prog.getCoordinator() != null && !prog.getCoordinator().isBlank()) {
            resolvedName = prog.getCoordinator();
        }
        if (resolvedEmail.isBlank() && prog.getCoordinatorEmail() != null && !prog.getCoordinatorEmail().isBlank()) {
            resolvedEmail = prog.getCoordinatorEmail();
        }

        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(targetProgId);
        List<String> batchIds = batches.stream().map(ProgrammeBatch::getId).toList();
        List<ProgrammeBatchCourse> courses = batchIds.isEmpty() ? List.of() : programmeBatchCourseRepository.findByProgrammeBatchIdInAndDeletedAtIsNull(batchIds);
        List<ProgrammeOutcome> pos = (!batches.isEmpty() ? programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(batches.get(0).getId()) : List.of());
        List<ProgrammeSpecificOutcome> psos = (!batches.isEmpty() ? programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(batches.get(0).getId()) : List.of());
        List<PeoOutcome> peos = (!batches.isEmpty() ? peoOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(batches.get(0).getId()) : List.of());

        ProgrammeCoordinatorSetupProgressDto progressDto = getProgrammeCoordinatorSetupProgress(coordinatorEmail, targetProgId);

        return ProgrammeCoordinatorSummaryDto.builder()
                .masterProgrammeId(prog.getId())
                .programmeCode(prog.getCode())
                .programmeName(prog.getName())
                .departmentId(prog.getDepartmentId())
                .departmentName(prog.getDepartmentName())
                .coordinatorName(resolvedName)
                .coordinatorEmail(resolvedEmail)
                .durationYears(prog.getDurationYears())
                .courseCount(courses.size())
                .activePOsCount(pos.size())
                .activePSOsCount(psos.size())
                .activePEOsCount(peos.size())
                .activeBatchesCount(batches.size())
                .pendingVerificationsCount(0)
                .assignedProgrammes(assignedProgrammes)
                .setupProgress(progressDto)
                .build();
    }

    @Transactional(readOnly = true)
    public ProgrammeCoordinatorSetupProgressDto getProgrammeCoordinatorSetupProgress(String coordinatorEmail, String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AcademicService] getProgrammeCoordinatorSetupProgress called | coordinatorEmail: " + coordinatorEmail + " | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId);

        String targetProgId = resolveTargetProgId(masterProgrammeId, coordinatorEmail);
        if (targetProgId == null || targetProgId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MasterProgramme scope cannot be determined.");
        }
        enforceProgrammeScope(targetProgId);

        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(targetProgId);
        ProgrammeBatch activeBatch = batches.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).findFirst()
                .orElse(!batches.isEmpty() ? batches.get(0) : null);

        String targetProgrammeBatchId = programmeBatchId;
        if (targetProgrammeBatchId == null || targetProgrammeBatchId.isBlank()) {
            targetProgrammeBatchId = activeBatch != null ? activeBatch.getId() : "batch-" + targetProgId;
        }

        final String finalProgId = targetProgId;
        final String finalProgrammeBatchId = targetProgrammeBatchId;

        ProgrammeCoordinatorSetupProgress progress = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            progress = pcSetupProgressRepository.findByProgrammeBatchId(programmeBatchId).orElse(null);
        } else {
            if (activeBatch != null) {
                progress = pcSetupProgressRepository.findByProgrammeBatchId(activeBatch.getId()).orElse(null);
            }
            if (progress == null) {
                progress = pcSetupProgressRepository.findByProgrammeBatchId(finalProgId).orElse(null);
            }
            if (progress == null) {
                for (ProgrammeBatch b : batches) {
                    progress = pcSetupProgressRepository.findByProgrammeBatchId(b.getId()).orElse(null);
                    if (progress != null) break;
                }
            }
        }
        if (progress == null) {
            progress = createDefaultPcProgress(finalProgId, finalProgrammeBatchId, coordinatorEmail);
        }

        return buildPcSetupProgressDto(progress, finalProgId);
    }

    @Transactional(readOnly = true)
    public ProgrammeCoordinatorSetupProgressDto getProgrammeCoordinatorSetupProgress(String coordinatorEmail, String masterProgrammeId) {
        return getProgrammeCoordinatorSetupProgress(coordinatorEmail, masterProgrammeId, null);
    }

    @Transactional
    public ProgrammeCoordinatorSetupProgressDto updateProgrammeCoordinatorSetupProgress(
            String coordinatorEmail, String masterProgrammeId, String programmeBatchId, Integer stepNumber, Map<String, Object> body) {
        System.out.println("[AcademicService] updateProgrammeCoordinatorSetupProgress called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId + " | stepNumber: " + stepNumber + " | coordinatorEmail: " + coordinatorEmail);

        String targetProgId = resolveTargetProgId(masterProgrammeId, coordinatorEmail);
        if (targetProgId == null || targetProgId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MasterProgramme scope cannot be determined.");
        }
        enforceProgrammeScope(targetProgId);

        List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(targetProgId);
        ProgrammeBatch activeBatch = batches.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).findFirst()
                .orElse(!batches.isEmpty() ? batches.get(0) : null);

        String targetProgrammeBatchId = programmeBatchId;
        if (targetProgrammeBatchId == null || targetProgrammeBatchId.isBlank()) {
            targetProgrammeBatchId = activeBatch != null ? activeBatch.getId() : "batch-" + targetProgId;
        }

        final String finalProgId = targetProgId;
        final String finalProgrammeBatchId = targetProgrammeBatchId;

        ProgrammeCoordinatorSetupProgress progress = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            progress = pcSetupProgressRepository.findByProgrammeBatchId(programmeBatchId).orElse(null);
        } else {
            if (activeBatch != null) {
                progress = pcSetupProgressRepository.findByProgrammeBatchId(activeBatch.getId()).orElse(null);
            }
            if (progress == null) {
                progress = pcSetupProgressRepository.findByProgrammeBatchId(finalProgId).orElse(null);
            }
            if (progress == null) {
                for (ProgrammeBatch b : batches) {
                    progress = pcSetupProgressRepository.findByProgrammeBatchId(b.getId()).orElse(null);
                    if (progress != null) break;
                }
            }
        }
        if (progress == null) {
            progress = createDefaultPcProgress(finalProgId, finalProgrammeBatchId, coordinatorEmail);
        }

        progress.setProgrammeBatchId(finalProgrammeBatchId);

        if (coordinatorEmail != null && !coordinatorEmail.isBlank()) {
            progress.setCoordinatorEmail(coordinatorEmail);
        }

        List<String> ALL_PC_STEPS = List.of("courses", "po_pso_target", "indirect_attainment", "programme_atr", "review");
        Set<String> completedSet = new LinkedHashSet<>();
        if (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank()) {
            for (String s : progress.getCompletedSteps().split(",")) {
                String clean = s.trim();
                String norm = normalizePcStepName(clean);
                if (norm != null && ALL_PC_STEPS.contains(norm)) {
                    completedSet.add(norm);
                }
            }
        }

        // Once completed, the completed list remains complete and should not change/revert
        if (progress.getOverallStatus() == SetupStepStatus.COMPLETED || completedSet.size() == ALL_PC_STEPS.size() || completedSet.contains("review")) {
            completedSet.addAll(ALL_PC_STEPS);
        }

        if (body != null && body.containsKey("completedSteps")) {
            Object csObj = body.get("completedSteps");
            if (csObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        String norm = normalizePcStepName(item);
                        if (norm != null && ALL_PC_STEPS.contains(norm)) {
                            completedSet.add(norm);
                        }
                    }
                }
            } else if (csObj instanceof String csStr) {
                for (String s : csStr.split(",")) {
                    String norm = normalizePcStepName(s);
                    if (norm != null && ALL_PC_STEPS.contains(norm)) {
                        completedSet.add(norm);
                    }
                }
            }
        } else if (body != null && body.containsKey("completedStep")) {
            String norm = normalizePcStepName(body.get("completedStep"));
            if (norm != null && ALL_PC_STEPS.contains(norm)) {
                completedSet.add(norm);
            }
        }

        if (completedSet.contains("review")) {
            completedSet.addAll(ALL_PC_STEPS);
        }

        List<String> canonicalCompleted = ALL_PC_STEPS.stream().filter(completedSet::contains).toList();
        boolean allDone = canonicalCompleted.size() == ALL_PC_STEPS.size() || completedSet.contains("review");

        int step;
        if (allDone) {
            step = 1; // Make currentStep pointing towards step 1 upon completion
            progress.setOverallStatus(SetupStepStatus.COMPLETED);
            progress.setPendingSteps("");
            canonicalCompleted = ALL_PC_STEPS;
        } else {
            step = stepNumber != null && stepNumber > 0 ? stepNumber : (progress.getCurrentStep() != null && progress.getCurrentStep() > 0 ? progress.getCurrentStep() : 1);
            if (!canonicalCompleted.isEmpty()) {
                progress.setOverallStatus(SetupStepStatus.IN_PROGRESS);
            } else {
                progress.setOverallStatus(SetupStepStatus.NOT_STARTED);
            }
            List<String> pending = ALL_PC_STEPS.stream().filter(s -> !completedSet.contains(s)).toList();
            progress.setPendingSteps(String.join(",", pending));
        }

        progress.setCurrentStep(step);
        progress.setCompletedSteps(String.join(",", canonicalCompleted));
        progress.setUpdatedAt(ZonedDateTime.now());
        pcSetupProgressRepository.save(progress);
        return buildPcSetupProgressDto(progress, finalProgId);
    }

    private String normalizePcStepName(Object step) {
        if (step == null) return null;
        String s = String.valueOf(step).trim().toLowerCase();
        return switch (s) {
            case "courses", "course", "add_courses", "add_course", "course_setup", "programme setup", "programme_setup" -> "courses";
            case "po_pso_target", "target", "targets", "po_pso_targets", "po_target", "po_targets", "po/pso target", "po/pso targets" -> "po_pso_target";
            case "indirect_attainment", "indirect", "survey", "exit_survey", "programme_survey", "indirect_programme_batch_attainment", "indirect attainment" -> "indirect_attainment";
            case "programme_atr", "atr", "programme_batch_atr", "atrs", "programme atr" -> "programme_atr";
            case "review", "confirm", "review_confirm", "review_and_confirm", "verify", "verify&finish", "verify_and_finish" -> "review";
            default -> s;
        };
    }

    @Transactional
    public ProgrammeCoordinatorSetupProgressDto updateProgrammeCoordinatorSetupProgress(String coordinatorEmail, String masterProgrammeId, Integer stepNumber) {
        return updateProgrammeCoordinatorSetupProgress(coordinatorEmail, masterProgrammeId, null, stepNumber, null);
    }

    @Transactional
    public ProgrammeCoordinatorSetupProgressDto completeProgrammeCoordinatorSetup(String coordinatorEmail, String masterProgrammeId, String programmeBatchId) {
        System.out.println("[AcademicService] completeProgrammeCoordinatorSetup called | masterProgrammeId: " + masterProgrammeId + " | programmeBatchId: " + programmeBatchId + " | coordinatorEmail: " + coordinatorEmail);
        Map<String, Object> body = Map.of("completedSteps", List.of("courses", "po_pso_target", "indirect_attainment", "programme_atr", "review"));
        return updateProgrammeCoordinatorSetupProgress(coordinatorEmail, masterProgrammeId, programmeBatchId, 1, body);
    }

    @Transactional
    public ProgrammeCoordinatorSetupProgressDto completeProgrammeCoordinatorSetup(String coordinatorEmail, String masterProgrammeId) {
        return completeProgrammeCoordinatorSetup(coordinatorEmail, masterProgrammeId, null);
    }

    private ProgrammeCoordinatorSetupProgress createDefaultPcProgress(String masterProgrammeId, String programmeBatchId, String coordinatorEmail) {
        String finalProgrammeBatchId = programmeBatchId;
        if (finalProgrammeBatchId == null || finalProgrammeBatchId.isBlank()) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(masterProgrammeId);
            finalProgrammeBatchId = !batches.isEmpty() ? batches.get(0).getId() : "batch-" + masterProgrammeId;
        }
        return ProgrammeCoordinatorSetupProgress.builder()
                .id("pcprog-" + UUID.randomUUID().toString().substring(0, 8))
                .programmeBatchId(finalProgrammeBatchId)
                .coordinatorEmail(coordinatorEmail)
                .currentStep(1)
                .overallStatus(SetupStepStatus.NOT_STARTED)
                .completedSteps("")
                .pendingSteps("")
                .updatedAt(ZonedDateTime.now())
                .build();
    }

    private ProgrammeCoordinatorSetupProgress createDefaultPcProgress(String masterProgrammeId, String coordinatorEmail) {
        return createDefaultPcProgress(masterProgrammeId, null, coordinatorEmail);
    }

    private ProgrammeCoordinatorSetupProgressDto buildPcSetupProgressDto(ProgrammeCoordinatorSetupProgress progress) {
        return buildPcSetupProgressDto(progress, null);
    }

    private ProgrammeCoordinatorSetupProgressDto buildPcSetupProgressDto(ProgrammeCoordinatorSetupProgress progress, String explicitProgId) {
        List<String> ALL_PC_STEPS = List.of("courses", "po_pso_target", "indirect_attainment", "programme_atr", "review");

        Set<String> completedSet = new LinkedHashSet<>();
        if (progress.getCompletedSteps() != null && !progress.getCompletedSteps().isBlank()) {
            for (String s : progress.getCompletedSteps().split(",")) {
                String clean = s.trim();
                String norm = normalizePcStepName(clean);
                if (norm != null && ALL_PC_STEPS.contains(norm)) {
                    completedSet.add(norm);
                }
            }
        }

        boolean isCompleted = progress.getOverallStatus() == SetupStepStatus.COMPLETED || completedSet.contains("review") || completedSet.size() == ALL_PC_STEPS.size();
        if (isCompleted) {
            completedSet.addAll(ALL_PC_STEPS);
        }

        List<String> completed = ALL_PC_STEPS.stream().filter(completedSet::contains).toList();
        List<String> pending = isCompleted ? List.of() : ALL_PC_STEPS.stream().filter(s -> !completedSet.contains(s)).toList();

        String progId = explicitProgId;
        if (progId == null || progId.isBlank()) {
            progId = progress.getMasterProgrammeId();
            if (progress.getProgrammeBatchId() != null) {
                ProgrammeBatch b = programmeBatchRepository.findById(progress.getProgrammeBatchId()).orElse(null);
                if (b != null && b.getMasterProgrammeId() != null) {
                    progId = b.getMasterProgrammeId();
                }
            }
        }

        int currentStep = isCompleted ? 1 : (progress.getCurrentStep() != null && progress.getCurrentStep() > 0 ? progress.getCurrentStep() : 1);

        return ProgrammeCoordinatorSetupProgressDto.builder()
                .id(progress.getId())
                .masterProgrammeId(progId)
                .programmeBatchId(progress.getProgrammeBatchId())
                .coordinatorEmail(progress.getCoordinatorEmail())
                .currentStep(currentStep)
                .overallStatus(isCompleted ? SetupStepStatus.COMPLETED : progress.getOverallStatus())
                .completedSteps(completed)
                .pendingSteps(pending != null ? pending : List.of())
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHodCoordinators(String departmentId) {
        CurrentUserScope scope = getScope();
        String targetDeptId = departmentId;
        if (scope != null && scope.isHod()) {
            targetDeptId = scope.getRequiredDepartmentId();
        }
        if (targetDeptId != null && !targetDeptId.isBlank()) {
            enforceDepartmentScope(targetDeptId);
        }
        List<MasterProgramme> progs = (targetDeptId != null && !targetDeptId.isBlank())
                ? masterProgrammeRepository.findByDepartmentIdAndDeletedAtIsNull(targetDeptId)
                : getAllProgrammes();

        List<Map<String, Object>> list = new ArrayList<>();
        for (MasterProgramme p : progs) {
            enrichProgrammeCoordinator(p);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("masterProgrammeId", p.getId());
            item.put("code", p.getCode());
            item.put("programmeCode", p.getCode());
            item.put("name", p.getName());
            item.put("programmeName", p.getName());
            item.put("durationYears", p.getDurationYears() != null ? p.getDurationYears() : 4);
            item.put("departmentId", p.getDepartmentId());
            item.put("departmentName", p.getDepartmentName());
            item.put("coordinator", p.getCoordinator() != null ? p.getCoordinator() : "Not Assigned");
            item.put("coordinatorName", p.getCoordinator() != null ? p.getCoordinator() : "Not Assigned");
            item.put("coordinatorEmail", p.getCoordinatorEmail() != null ? p.getCoordinatorEmail() : "");
            item.put("status", p.getStatus() != null ? p.getStatus() : "ACTIVE");
            item.put("assignedDate", p.getUpdatedAt() != null ? p.getUpdatedAt().toLocalDate().toString() : "2025-06-15");
            list.add(item);
        }
        return list;
    }

    @Transactional
    public Map<String, Object> assignHodCoordinator(Map<String, Object> payload) {
        String progId = payload != null && payload.get("masterProgrammeId") != null
                ? payload.get("masterProgrammeId").toString()
                : (payload != null && payload.get("id") != null ? payload.get("id").toString() : null);
        String name = payload != null && payload.get("coordinatorName") != null
                ? payload.get("coordinatorName").toString()
                : (payload != null && payload.get("coordinator") != null ? payload.get("coordinator").toString() : "");
        String email = payload != null && payload.get("coordinatorEmail") != null
                ? payload.get("coordinatorEmail").toString()
                : "";

        if (progId != null) {
            enforceProgrammeScope(progId);
            MasterProgramme p = masterProgrammeRepository.findByIdAndDeletedAtIsNull(progId).orElse(null);
            if (p != null) {
                p.setCoordinator(name);
                p.setCoordinatorEmail(email);
                saveProgramme(p);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "MasterProgramme coordinator assigned successfully.");
        if (auditLogService != null && progId != null) {
            auditLogService.recordSuccess(com.dypiu.nba.audit.AuditAction.ASSIGN_COORDINATOR, com.dypiu.nba.audit.ResourceType.MASTER_PROGRAMME, progId, null, null, "Assigned PC coordinator " + name, java.util.Map.of("coordinatorEmail", email != null ? email : ""));
        }
        return res;
    }

    @Transactional
    public Map<String, Object> allocateCourses(String masterProgrammeId, String programmeBatchId, List<Map<String, Object>> allocations) {
        return allocateCourses(masterProgrammeId, programmeBatchId, allocations, true);
    }

    @Transactional
    public Map<String, Object> allocateCourses(String masterProgrammeId, String programmeBatchId, List<Map<String, Object>> allocations, boolean submit) {
        Integer targetSemester = null;
        if (allocations != null && !allocations.isEmpty()) {
            for (Map<String, Object> item : allocations) {
                if (item.get("semester") != null) {
                    try {
                        targetSemester = Integer.parseInt(item.get("semester").toString().trim());
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }
        if (targetSemester == null) targetSemester = 1;

        ProgrammeBatch batch = null;
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            batch = programmeBatchRepository.findById(programmeBatchId)
                    .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                    .orElse(null);
        }

        String targetBatchId = batch != null ? batch.getId() : (programmeBatchId != null ? programmeBatchId.trim() : null);

        if (targetBatchId != null) {
            enforceBatchScope(targetBatchId);
            Set<Integer> affectedSemesters = new LinkedHashSet<>();
            if (allocations != null && !allocations.isEmpty()) {
                for (Map<String, Object> item : allocations) {
                    if (item.get("semester") != null) {
                        try {
                            affectedSemesters.add(Integer.parseInt(item.get("semester").toString().trim()));
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (affectedSemesters.isEmpty()) {
                affectedSemesters.add(targetSemester != null ? targetSemester : 1);
            }
            for (Integer sem : affectedSemesters) {
                enforceSemesterAllocationEditability(targetBatchId, sem);
            }
        } else if (masterProgrammeId != null) {
            enforceProgrammeScope(masterProgrammeId);
            if (isAllocationApproved("allocation-" + masterProgrammeId) || isAllocationApproved(masterProgrammeId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Course Allocation. A revision must be requested first.");
            }
        }

        if (allocations != null) {
            for (Map<String, Object> item : allocations) {
                String rawCourseId = null;
                if (item.get("programmeBatchCourseId") != null) rawCourseId = item.get("programmeBatchCourseId").toString().trim();
                else if (item.get("id") != null) rawCourseId = item.get("id").toString().trim();
                else if (item.get("courseOfferingId") != null) rawCourseId = item.get("courseOfferingId").toString().trim();
                else if (item.get("courseId") != null) rawCourseId = item.get("courseId").toString().trim();
                else if (item.get("masterCourseId") != null) rawCourseId = item.get("masterCourseId").toString().trim();

                String email = item.get("coordinatorEmail") != null ? item.get("coordinatorEmail").toString().trim() : (item.get("courseCoordinatorEmail") != null ? item.get("courseCoordinatorEmail").toString().trim() : "");
                String name = item.get("courseCoordinatorName") != null ? item.get("courseCoordinatorName").toString().trim() : (item.get("coordinator") != null ? item.get("coordinator").toString().trim() : "");
                String code = item.get("courseCode") != null ? item.get("courseCode").toString().trim() : (item.get("code") != null ? item.get("code").toString().trim() : null);
                String cname = item.get("courseName") != null ? item.get("courseName").toString().trim() : (item.get("name") != null ? item.get("name").toString().trim() : null);
                Integer credits = null;
                if (item.get("credits") != null) {
                    try { credits = Integer.parseInt(item.get("credits").toString().trim()); } catch (Exception ignored) {}
                }
                String courseType = item.get("courseType") != null ? item.get("courseType").toString().trim() : "THEORY";

                String semStr = item.get("semester") != null ? item.get("semester").toString().trim() : null;
                Integer parsedSem = (semStr != null && semStr.matches("\\d+")) ? Integer.parseInt(semStr) : targetSemester;

                if (rawCourseId != null && !rawCourseId.isBlank()) {
                    enforceCourseScope(rawCourseId);
                }

                // Resolve coordinator user if email is present
                User coordinatorUser = null;
                if (!email.isBlank()) {
                    coordinatorUser = userRepository.findByEmail(email).orElse(null);
                }

                // 1. Try to find existing ProgrammeBatchCourse
                ProgrammeBatchCourse targetOffering = null;
                if (rawCourseId != null && !rawCourseId.isBlank()) {
                    targetOffering = programmeBatchCourseRepository.findById(rawCourseId).orElse(null);
                }

                if (targetOffering == null && targetBatchId != null) {
                    if (rawCourseId != null) {
                        targetOffering = programmeBatchCourseRepository.findByMasterCourseId(rawCourseId).stream()
                                .filter(o -> targetBatchId.equals(o.getProgrammeBatchId()))
                                .findFirst()
                                .orElse(null);
                    }
                    if (targetOffering == null && code != null && !code.isBlank()) {
                        targetOffering = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(targetBatchId).stream()
                                .filter(o -> code.equalsIgnoreCase(o.getCode()) || code.equalsIgnoreCase(o.getCourseCodeOverride()))
                                .findFirst()
                                .orElse(null);
                    }
                }

                // 2. If targetOffering found, update it directly
                if (targetOffering != null) {
                    if (code != null && !code.isBlank()) targetOffering.setCode(code);
                    if (cname != null && !cname.isBlank()) targetOffering.setName(cname);
                    if (credits != null) targetOffering.setCredits(credits);
                    if (courseType != null) targetOffering.setCourseType(courseType);
                    if (parsedSem != null) targetOffering.setSemester(parsedSem);
                    if (!name.isBlank()) targetOffering.setCourseCoordinatorName(name);
                    if (!email.isBlank() || !name.isBlank()) targetOffering.setAssignedFaculty(name + (email.isBlank() ? "" : " (" + email + ")"));
                    if (coordinatorUser != null) {
                        targetOffering.setCourseCoordinatorId(coordinatorUser.getId());
                    }
                    targetOffering.setUpdatedAt(ZonedDateTime.now());
                    programmeBatchCourseRepository.save(targetOffering);
                } else if (targetBatchId != null && !targetBatchId.isBlank()) {
                    // 3. Create new ProgrammeBatchCourse directly under the batch
                    String newId = (rawCourseId != null && rawCourseId.startsWith("off-")) ? rawCourseId : ("off-" + UUID.randomUUID().toString().substring(0, 8));
                    targetOffering = ProgrammeBatchCourse.builder()
                            .id(newId)
                            .masterCourseId(rawCourseId)
                            .programmeBatchId(targetBatchId)
                            .code(code != null ? code : "COURSE")
                            .name(cname != null ? cname : "Course")
                            .credits(credits != null ? credits : 3)
                            .courseType(courseType != null ? courseType : "THEORY")
                            .semester(parsedSem != null ? parsedSem : (targetSemester != null ? targetSemester : 1))
                            .courseCoordinatorName(name)
                            .assignedFaculty(name + (email.isBlank() ? "" : " (" + email + ")"))
                            .courseCoordinatorId(coordinatorUser != null ? coordinatorUser.getId() : null)
                            .status("ACTIVE")
                            .build();
                    programmeBatchCourseRepository.save(targetOffering);
                }
            }
        }

        if (submit) {
            String progId = batch != null ? batch.getMasterProgrammeId() : masterProgrammeId;
            String semKey = targetBatchId != null ? ("allocation-" + targetBatchId + "-sem-" + targetSemester) : ("allocation-" + masterProgrammeId);
            String reqTitle = (targetBatchId != null)
                    ? "Course Allocation: " + (batch != null ? batch.getName() : targetBatchId) + " (Semester " + targetSemester + ")"
                    : "MasterCourse Allocation for MasterProgramme " + masterProgrammeId;

            ApprovalRequest req = ApprovalRequest.builder()
                    .id("app-alloc-" + UUID.randomUUID().toString().substring(0, 8))
                    .type(ApprovalType.COURSE_ALLOCATION)
                    .title(reqTitle)
                    .masterProgrammeId(progId)
                    .programmeBatchId(targetBatchId)
                    .resourceId(semKey)
                    .status(ApprovalStatus.PENDING)
                    .submittedBy("Programme Coordinator")
                    .submittedAt(ZonedDateTime.now())
                    .remarks("Allocations for Semester " + targetSemester + " submitted for HOD review.")
                    .build();
            approvalRequestRepository.save(req);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", submit ? "Course allocations saved and submitted for verification." : "Course allocations saved successfully.");
        return res;
    }

    @Transactional(readOnly = true)
    public boolean isSemesterCompleted(String programmeBatchId, Integer semester) {
        if (programmeBatchId == null || semester == null) return false;
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElse(null);
        if (batch == null) return false;
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId())
                .stream()
                .filter(o -> Objects.equals(o.getSemester(), semester))
                .toList();
        if (offerings.isEmpty()) return false;
        return offerings.stream().allMatch(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()));
    }

    @Transactional(readOnly = true)
    public void enforceSemesterAllocationEditability(String programmeBatchId, Integer semester) {
        if (programmeBatchId == null || semester == null) return;
        if (isSemesterCompleted(programmeBatchId, semester)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify allocations: Semester " + semester + " is COMPLETED and locked.");
        }
        String semKey = "allocation-" + programmeBatchId.trim() + "-sem-" + semester;
        ApprovalRequest req = approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.COURSE_ALLOCATION && semKey.equalsIgnoreCase(a.getResourceId()))
                .max(LATEST_APPROVAL_COMPARATOR)
                .orElse(null);
        if (req != null) {
            ApprovalStatus status = req.getStatus();
            if (status == ApprovalStatus.PENDING || status == ApprovalStatus.SUBMITTED || status == ApprovalStatus.PENDING_APPROVAL) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify allocations: Course Allocation for Semester " + semester + " has already been submitted for HOD review and is awaiting approval.");
            }
            if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.VERIFIED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Course Allocation for Semester " + semester + ". A revision must be requested first.");
            }
        } else {
            if (isAllocationApproved("allocation-" + programmeBatchId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify approved Course Allocation for Semester " + semester + ". A revision must be requested first.");
            }
        }
    }

    @Transactional(readOnly = true)
    public com.dypiu.nba.dto.SemesterReadinessDto getSemesterReadiness(String programmeBatchId, Integer semester) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + programmeBatchId));

        enforceBatchScope(batch.getId());

        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId())
                .stream()
                .filter(o -> Objects.equals(o.getSemester(), semester))
                .toList();

        List<com.dypiu.nba.dto.SemesterReadinessDto.ReadinessWarning> warnings = new ArrayList<>();
        int readyCount = 0;

        String semKey = "allocation-" + batch.getId() + "-sem-" + semester;
        boolean allocationApproved = isAllocationApproved(semKey);
        if (!allocationApproved) {
            warnings.add(com.dypiu.nba.dto.SemesterReadinessDto.ReadinessWarning.builder()
                    .issue("ALLOCATION_NOT_APPROVED")
                    .message("Course allocation for Semester " + semester + " has not been approved by the HOD.")
                    .build());
        }

        for (ProgrammeBatchCourse off : offerings) {
            boolean courseReady = true;

            // 1. COs & Targets
            boolean hasCos = courseOutcomeRepository.findByProgrammeBatchCourseId(off.getId())
                    .stream().findAny().isPresent();
            if (!hasCos) {
                courseReady = false;
                warnings.add(com.dypiu.nba.dto.SemesterReadinessDto.ReadinessWarning.builder()
                        .programmeBatchCourseId(off.getId())
                        .courseCode(off.getCode())
                        .courseName(off.getName())
                        .issue("CO_TARGETS_INCOMPLETE")
                        .message("Course Outcomes / targets are incomplete or not configured for course " + off.getCode() + ".")
                        .build());
            }

            // 2. Attainment Settings
            boolean hasConfig = configRepository.findByProgrammeBatchCourseId(off.getId()).isPresent();
            if (!hasConfig) {
                courseReady = false;
                warnings.add(com.dypiu.nba.dto.SemesterReadinessDto.ReadinessWarning.builder()
                        .programmeBatchCourseId(off.getId())
                        .courseCode(off.getCode())
                        .courseName(off.getName())
                        .issue("ATTAINMENT_SETTINGS_INCOMPLETE")
                        .message("Attainment settings/configuration not completed for course " + off.getCode() + ".")
                        .build());
            }

            // 3. Course ATR
            boolean atrApproved = isCourseAtrApprovedInternal(off.getId());
            if (!atrApproved) {
                courseReady = false;
                warnings.add(com.dypiu.nba.dto.SemesterReadinessDto.ReadinessWarning.builder()
                        .programmeBatchCourseId(off.getId())
                        .courseCode(off.getCode())
                        .courseName(off.getName())
                        .issue("COURSE_ATR_NOT_APPROVED")
                        .message("Course ATR is not approved for course " + off.getCode() + ".")
                        .build());
            }

            if (courseReady) {
                readyCount++;
            }
        }

        boolean isCompleted = isSemesterCompleted(batch.getId(), semester);

        return com.dypiu.nba.dto.SemesterReadinessDto.builder()
                .programmeBatchId(batch.getId())
                .batchName(batch.getName())
                .semester(semester)
                .status(isCompleted ? "COMPLETED" : (allocationApproved ? "ALLOCATION_APPROVED" : "DRAFT"))
                .isCompleted(isCompleted)
                .canComplete(true)
                .courseCount(offerings.size())
                .readyCourseCount(readyCount)
                .warnings(warnings)
                .build();
    }

    private boolean isCourseAtrApprovedInternal(String batchCourseId) {
        if (batchCourseId == null || batchCourseId.isBlank()) return false;
        ApprovalRequest atrReq = approvalRequestRepository.findAll().stream()
                .filter(a -> a.getType() == ApprovalType.COURSE_ATR && (batchCourseId.equalsIgnoreCase(a.getProgrammeBatchCourseId()) || batchCourseId.equalsIgnoreCase(a.getResourceId())))
                .max(LATEST_APPROVAL_COMPARATOR)
                .orElse(null);
        if (atrReq != null) return atrReq.getStatus() == ApprovalStatus.APPROVED;
        List<CourseAtr> atrs = courseAtrRepository.findByProgrammeBatchCourseId(batchCourseId);
        return !atrs.isEmpty() && atrs.stream().allMatch(a -> a.getStatus() == CourseAtrStatus.APPROVED);
    }

    @Transactional
    public Map<String, Object> completeSemester(String programmeBatchId, Integer semester, String reason) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + programmeBatchId));

        CurrentUserScope scope = getScope();
        if (scope == null || (!scope.isHod() && !scope.isDirector() && !scope.isIqac())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only HOD or higher authority can complete a semester.");
        }
        enforceBatchScope(batch.getId());

        if ("GRADUATED".equalsIgnoreCase(batch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify semester on GRADUATED batch.");
        }

        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId())
                .stream()
                .filter(o -> Objects.equals(o.getSemester(), semester))
                .toList();

        if (offerings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No courses found for Semester " + semester);
        }

        for (ProgrammeBatchCourse off : offerings) {
            off.setStatus("COMPLETED");
            off.setUpdatedAt(ZonedDateTime.now());
            programmeBatchCourseRepository.save(off);
        }

        String actorName = (scope.getEmail() != null) ? scope.getEmail() : (scope.getUsername() != null ? scope.getUsername() : scope.getName());
        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH,
                    batch.getId(),
                    "SEMESTER_" + semester + "_ACTIVE",
                    "SEMESTER_" + semester + "_COMPLETED",
                    reason != null && !reason.isBlank() ? reason : "Semester " + semester + " completed by HOD",
                    Map.of("semester", semester, "completedBy", actorName != null ? actorName : "")
            );
        }

        return Map.of(
                "success", true,
                "message", "Semester " + semester + " completed successfully.",
                "programmeBatchId", batch.getId(),
                "semester", semester,
                "status", "COMPLETED"
        );
    }

    @Transactional
    public Map<String, Object> reopenSemester(String programmeBatchId, Integer semester, String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mandatory reason is required to reopen a completed semester.");
        }

        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + programmeBatchId));

        CurrentUserScope scope = getScope();
        if (scope == null || (!scope.isHod() && !scope.isDirector() && !scope.isIqac())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only HOD or higher authority can reopen a semester.");
        }
        enforceBatchScope(batch.getId());

        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId())
                .stream()
                .filter(o -> Objects.equals(o.getSemester(), semester))
                .toList();

        for (ProgrammeBatchCourse off : offerings) {
            off.setStatus("ACTIVE");
            off.setUpdatedAt(ZonedDateTime.now());
            programmeBatchCourseRepository.save(off);
        }

        String actorName = (scope.getEmail() != null) ? scope.getEmail() : (scope.getUsername() != null ? scope.getUsername() : scope.getName());
        if (auditLogService != null) {
            auditLogService.recordSuccess(
                    com.dypiu.nba.audit.AuditAction.UPDATE,
                    com.dypiu.nba.audit.ResourceType.PROGRAMME_BATCH,
                    batch.getId(),
                    "SEMESTER_" + semester + "_COMPLETED",
                    "SEMESTER_" + semester + "_REOPENED",
                    reason.trim(),
                    Map.of("semester", semester, "reopenedBy", actorName != null ? actorName : "", "reason", reason.trim())
            );
        }

        return Map.of(
                "success", true,
                "message", "Semester " + semester + " reopened successfully.",
                "programmeBatchId", batch.getId(),
                "semester", semester,
                "status", "NOT_COMPLETED"
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSemestersStatusOverview(String programmeBatchId) {
        ProgrammeBatch batch = programmeBatchRepository.findById(programmeBatchId)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(programmeBatchId.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme Batch not found: " + programmeBatchId));

        int durationYears = batch.getDurationYears() != null ? batch.getDurationYears() : 4;
        int maxSemesters = durationYears * 2;

        List<ProgrammeBatchCourse> allOfferings = programmeBatchCourseRepository.findByProgrammeBatchIdAndDeletedAtIsNull(batch.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (int sem = 1; sem <= maxSemesters; sem++) {
            final int currentSem = sem;
            List<ProgrammeBatchCourse> semOfferings = allOfferings.stream()
                    .filter(o -> Objects.equals(o.getSemester(), currentSem))
                    .toList();

            boolean isCompleted = isSemesterCompleted(batch.getId(), currentSem);
            String semKey = "allocation-" + batch.getId() + "-sem-" + currentSem;
            boolean isAllocApproved = isAllocationApproved(semKey);

            String allocStatus = "DRAFT";
            ApprovalRequest req = approvalRequestRepository.findAll().stream()
                    .filter(a -> a.getType() == ApprovalType.COURSE_ALLOCATION && semKey.equalsIgnoreCase(a.getResourceId()))
                    .max(LATEST_APPROVAL_COMPARATOR)
                    .orElse(null);
            if (req != null) {
                allocStatus = req.getStatus() != null ? req.getStatus().name() : "DRAFT";
            }

            String status;
            if (isCompleted) {
                status = "COMPLETED";
            } else if (isAllocApproved) {
                status = "ALLOCATION_APPROVED";
            } else if (req != null && (req.getStatus() == ApprovalStatus.SUBMITTED || req.getStatus() == ApprovalStatus.PENDING || req.getStatus() == ApprovalStatus.PENDING_APPROVAL)) {
                status = "SUBMITTED_FOR_VERIFICATION";
            } else if (req != null && (req.getStatus() == ApprovalStatus.REVISION_REQUESTED || req.getStatus() == ApprovalStatus.NEEDS_REVISION)) {
                status = "REVISION_REQUESTED";
            } else if (!semOfferings.isEmpty()) {
                status = "DRAFT";
            } else {
                status = "EMPTY";
            }

            Map<String, Object> semMap = new LinkedHashMap<>();
            semMap.put("semester", currentSem);
            semMap.put("status", status);
            semMap.put("isCompleted", isCompleted);
            semMap.put("allocationApproved", isAllocApproved);
            semMap.put("allocationStatus", allocStatus);
            semMap.put("courseCount", semOfferings.size());
            result.add(semMap);
        }
        return result;
    }

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

    public boolean isAllocationApproved(String masterProgrammeId) {
        if (masterProgrammeId == null || masterProgrammeId.isBlank()) return false;
        String clean = masterProgrammeId.trim();
        String progId = clean.replace("allocation-", "").replace("allocation_", "").replace("allocation", "").trim();
        return approvalRequestRepository.findAll().stream()
                .filter(a -> (a.getType() == ApprovalType.COURSE_ALLOCATION || a.getType() == ApprovalType.COURSE_OFFERING)
                        && (clean.equalsIgnoreCase(a.getResourceId())
                        || ("allocation-" + progId).equalsIgnoreCase(a.getResourceId())
                        || progId.equalsIgnoreCase(a.getResourceId())
                        || (progId.equalsIgnoreCase(a.getMasterProgrammeId()) && (a.getResourceId() == null || !a.getResourceId().contains("-sem-")))))
                .max(LATEST_APPROVAL_COMPARATOR)
                .map(a -> a.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConsolidatedOutcomes(String masterProgrammeId, String programmeBatchId) {
        if (masterProgrammeId != null && !masterProgrammeId.isBlank()) {
            enforceProgrammeScope(masterProgrammeId.trim());
        }
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            enforceBatchScope(programmeBatchId.trim());
        }
        if (masterProgrammeId == null || masterProgrammeId.isBlank()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("masterProgrammeId", null);
            data.put("programmeBatchId", programmeBatchId);
            data.put("pos", Collections.emptyList());
            data.put("psos", Collections.emptyList());
            data.put("peos", Collections.emptyList());
            return data;
        }
        String pId = masterProgrammeId.trim();
        String targetProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId.trim() : null;
        if (targetProgrammeBatchId == null) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(pId);
            for (ProgrammeBatch b : batches) {
                if (!programmeOutcomeRepository.findByProgrammeBatchId(b.getId()).isEmpty()) {
                    targetProgrammeBatchId = b.getId();
                    break;
                }
            }
            if (targetProgrammeBatchId == null && !batches.isEmpty()) {
                targetProgrammeBatchId = batches.get(0).getId();
            }
        }
        List<ProgrammeOutcome> pos = (targetProgrammeBatchId != null) ? new ArrayList<>(programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId)) : new ArrayList<>();
        for (ProgrammeOutcome po : pos) {
            List<PoCompetency> comps = new ArrayList<>(poCompetencyRepository.findByPoIdOrderByCodeAsc(po.getId()));
            comps.sort(Comparator.comparing(PoCompetency::getCode, NATURAL_CODE_COMPARATOR));
            po.setCompetencies(comps);
        }
        pos.sort(Comparator.comparing(ProgrammeOutcome::getCode, NATURAL_CODE_COMPARATOR));

        List<ProgrammeSpecificOutcome> psos = (targetProgrammeBatchId != null) ? new ArrayList<>(programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId)) : new ArrayList<>();
        for (ProgrammeSpecificOutcome pso : psos) {
            List<PsoCompetency> comps = new ArrayList<>(psoCompetencyRepository.findByPsoIdOrderByCodeAsc(pso.getId()));
            comps.sort(Comparator.comparing(PsoCompetency::getCode, NATURAL_CODE_COMPARATOR));
            pso.setCompetencies(comps);
        }
        psos.sort(Comparator.comparing(ProgrammeSpecificOutcome::getCode, NATURAL_CODE_COMPARATOR));

        List<PeoOutcome> peos = (targetProgrammeBatchId != null) ? new ArrayList<>(peoOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId)) : new ArrayList<>();
        peos.sort(Comparator.comparing(PeoOutcome::getCode, NATURAL_CODE_COMPARATOR));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("masterProgrammeId", pId);
        data.put("programmeBatchId", programmeBatchId != null ? programmeBatchId : targetProgrammeBatchId);
        data.put("pos", pos);
        data.put("psos", psos);
        data.put("peos", peos);
        return data;
    }

    @Transactional
    public Map<String, Object> saveConsolidatedOutcomes(Map<String, Object> payload) {
        String progId = payload != null && payload.get("masterProgrammeId") != null ? payload.get("masterProgrammeId").toString().trim() : null;
        if (progId == null || progId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MasterProgramme ID is required to save outcomes.");
        }
        enforceProgrammeScope(progId);

        String programmeBatchId = payload != null && payload.get("programmeBatchId") != null ? payload.get("programmeBatchId").toString().trim() : null;
        String targetProgrammeBatchId = (programmeBatchId != null && !programmeBatchId.isBlank()) ? programmeBatchId : null;
        if (targetProgrammeBatchId == null) {
            List<ProgrammeBatch> batches = programmeBatchRepository.findByMasterProgrammeId(progId);
            if (!batches.isEmpty()) {
                targetProgrammeBatchId = batches.get(0).getId();
            } else {
                targetProgrammeBatchId = progId;
            }
        }

        // 1. Process and save POs
        if (payload != null && payload.get("pos") instanceof List<?> poList) {
            List<ProgrammeOutcome> existingPOs = programmeOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId);
            if (!existingPOs.isEmpty()) {
                for (ProgrammeOutcome existingPo : existingPOs) {
                    poCompetencyRepository.deleteByPoId(existingPo.getId());
                }
                poCompetencyRepository.flush();
                programmeOutcomeRepository.deleteAll(existingPOs);
                programmeOutcomeRepository.flush();
            }

            for (Object obj : poList) {
                if (obj instanceof Map<?, ?> poMap) {
                    String code = poMap.get("code") != null ? poMap.get("code").toString() : null;
                    String statement = poMap.get("statement") != null ? poMap.get("statement").toString() : "";
                    BigDecimal target = new BigDecimal("2.50");
                    if (poMap.get("target") != null) {
                        try {
                            target = new BigDecimal(poMap.get("target").toString());
                        } catch (Exception ignored) {}
                    }
                    if (code != null && !code.isBlank()) {
                        String poId = poMap.get("id") != null ? poMap.get("id").toString() : null;
                        if (poId == null || poId.isBlank()) {
                            poId = "po-" + progId + "-" + code.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 6);
                        }
                        ProgrammeOutcome po = ProgrammeOutcome.builder()
                                .id(poId)
                                .programmeBatchId(targetProgrammeBatchId)
                                .code(code.trim().toUpperCase())
                                .statement(statement.trim())
                                .target(target)
                                .build();
                        programmeOutcomeRepository.save(po);

                        if (poMap.get("competencies") instanceof List<?> compList) {
                            List<PoCompetency> compsToSave = new ArrayList<>();
                            int cIdx = 1;
                            for (Object cObj : compList) {
                                if (cObj instanceof Map<?, ?> compMap) {
                                    String cStatement = compMap.get("statement") != null ? compMap.get("statement").toString() : "";
                                    if (cStatement.isBlank()) continue;
                                    String cCode = compMap.get("code") != null ? compMap.get("code").toString() : (po.getCode() + "." + cIdx);
                                    String cId = compMap.get("id") != null ? compMap.get("id").toString() : null;
                                    if (cId == null || cId.isBlank() || cId.startsWith("comp-")) {
                                        cId = "pocomp-" + UUID.randomUUID().toString().substring(0, 8);
                                    }
                                    cIdx++;
                                    compsToSave.add(PoCompetency.builder()
                                            .id(cId)
                                            .poId(po.getId())
                                            .code(cCode.trim())
                                            .statement(cStatement.trim())
                                            .build());
                                }
                            }
                            if (!compsToSave.isEmpty()) {
                                compsToSave.sort(Comparator.comparing(PoCompetency::getCode, NATURAL_CODE_COMPARATOR));
                                poCompetencyRepository.saveAll(compsToSave);
                                poCompetencyRepository.flush();
                            }
                        }
                    }
                }
            }
        }

        // 2. Process and save PSOs
        if (payload != null && payload.get("psos") instanceof List<?> psoList) {
            List<ProgrammeSpecificOutcome> existingPSOs = programmeSpecificOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId);
            if (!existingPSOs.isEmpty()) {
                for (ProgrammeSpecificOutcome existingPso : existingPSOs) {
                    psoCompetencyRepository.deleteByPsoId(existingPso.getId());
                }
                psoCompetencyRepository.flush();
                programmeSpecificOutcomeRepository.deleteAll(existingPSOs);
                programmeSpecificOutcomeRepository.flush();
            }

            for (Object obj : psoList) {
                if (obj instanceof Map<?, ?> psoMap) {
                    String code = psoMap.get("code") != null ? psoMap.get("code").toString() : null;
                    String statement = psoMap.get("statement") != null ? psoMap.get("statement").toString() : "";
                    BigDecimal target = new BigDecimal("2.50");
                    if (psoMap.get("target") != null) {
                        try {
                            target = new BigDecimal(psoMap.get("target").toString());
                        } catch (Exception ignored) {}
                    }
                    if (code != null && !code.isBlank()) {
                        String psoId = psoMap.get("id") != null ? psoMap.get("id").toString() : null;
                        if (psoId == null || psoId.isBlank()) {
                            psoId = "pso-" + progId + "-" + code.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 6);
                        }
                        ProgrammeSpecificOutcome pso = ProgrammeSpecificOutcome.builder()
                                .id(psoId)
                                .programmeBatchId(targetProgrammeBatchId)
                                .code(code.trim().toUpperCase())
                                .statement(statement.trim())
                                .target(target)
                                .build();
                        programmeSpecificOutcomeRepository.save(pso);

                        if (psoMap.get("competencies") instanceof List<?> compList) {
                            List<PsoCompetency> compsToSave = new ArrayList<>();
                            int cIdx = 1;
                            for (Object cObj : compList) {
                                if (cObj instanceof Map<?, ?> compMap) {
                                    String cStatement = compMap.get("statement") != null ? compMap.get("statement").toString() : "";
                                    if (cStatement.isBlank()) continue;
                                    String cCode = compMap.get("code") != null ? compMap.get("code").toString() : (pso.getCode() + "." + cIdx);
                                    String cId = compMap.get("id") != null ? compMap.get("id").toString() : null;
                                    if (cId == null || cId.isBlank() || cId.startsWith("comp-")) {
                                        cId = "psocomp-" + UUID.randomUUID().toString().substring(0, 8);
                                    }
                                    cIdx++;
                                    compsToSave.add(PsoCompetency.builder()
                                            .id(cId)
                                            .psoId(pso.getId())
                                            .code(cCode.trim())
                                            .statement(cStatement.trim())
                                            .build());
                                }
                            }
                            if (!compsToSave.isEmpty()) {
                                compsToSave.sort(Comparator.comparing(PsoCompetency::getCode, NATURAL_CODE_COMPARATOR));
                                psoCompetencyRepository.saveAll(compsToSave);
                                psoCompetencyRepository.flush();
                            }
                        }
                    }
                }
            }
        }

        // 3. Process and save PEOs
        if (payload != null && payload.get("peos") instanceof List<?> peoList) {
            List<PeoOutcome> existingPEOs = peoOutcomeRepository.findByProgrammeBatchIdOrderByCodeAsc(targetProgrammeBatchId);
            if (!existingPEOs.isEmpty()) {
                peoOutcomeRepository.deleteAll(existingPEOs);
                peoOutcomeRepository.flush();
            }

            for (Object obj : peoList) {
                if (obj instanceof Map<?, ?> peoMap) {
                    String code = peoMap.get("code") != null ? peoMap.get("code").toString() : null;
                    String statement = peoMap.get("statement") != null ? peoMap.get("statement").toString() : "";
                    if (code != null && !code.isBlank()) {
                        String peoId = peoMap.get("id") != null ? peoMap.get("id").toString() : null;
                        if (peoId == null || peoId.isBlank()) {
                            peoId = "peo-" + progId + "-" + code.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" + UUID.randomUUID().toString().substring(0, 6);
                        }
                        PeoOutcome peo = PeoOutcome.builder()
                                .id(peoId)
                                .programmeBatchId(targetProgrammeBatchId)
                                .code(code.trim().toUpperCase())
                                .statement(statement.trim())
                                .build();
                        peoOutcomeRepository.save(peo);
                    }
                }
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Outcomes saved successfully.");
        res.put("data", getConsolidatedOutcomes(progId, targetProgrammeBatchId));
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCourseCoTargets(String masterCourseId, String programmeBatchId) {
        if (masterCourseId != null && !masterCourseId.isBlank()) {
            enforceCourseScope(masterCourseId);
        }
        if (programmeBatchId != null && !programmeBatchId.isBlank()) {
            enforceBatchScope(programmeBatchId);
        }
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
        String offeringId = !offerings.isEmpty() ? offerings.get(0).getId() : masterCourseId;
        List<CourseOutcome> cos = courseOutcomeRepository.findByProgrammeBatchCourseId(offeringId);

        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        for (CourseOutcome co : cos) {
            targets.put(co.getCode(), co.getTargetLevel() != null ? co.getTargetLevel() : new BigDecimal("2.50"));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("masterCourseId", masterCourseId);
        res.put("programmeBatchId", programmeBatchId);
        res.put("coTargets", targets);
        return res;
    }

    @Transactional
    public Map<String, Object> saveCourseCoTargets(String masterCourseId, Map<String, Object> coTargets) {
        if (masterCourseId != null && !masterCourseId.isBlank()) {
            enforceCourseScope(masterCourseId);
        }
        List<ProgrammeBatchCourse> offerings = programmeBatchCourseRepository.findByMasterCourseId(masterCourseId);
        String offeringId = !offerings.isEmpty() ? offerings.get(0).getId() : masterCourseId;
        List<CourseOutcome> cos = courseOutcomeRepository.findByProgrammeBatchCourseId(offeringId);

        if (coTargets != null) {
            for (CourseOutcome co : cos) {
                if (coTargets.containsKey(co.getCode())) {
                    Object val = coTargets.get(co.getCode());
                    if (val instanceof Number) {
                        co.setTargetLevel(BigDecimal.valueOf(((Number) val).doubleValue()));
                    } else if (val instanceof String) {
                        try {
                            co.setTargetLevel(new BigDecimal((String) val));
                        } catch (Exception ignored) {}
                    }
                    courseOutcomeRepository.save(co);
                }
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "CO targets saved successfully.");
        res.put("data", getCourseCoTargets(masterCourseId, null));
        return res;
    }
}
