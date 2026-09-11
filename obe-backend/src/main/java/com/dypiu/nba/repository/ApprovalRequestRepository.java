package com.dypiu.nba.repository;

import com.dypiu.nba.entity.ApprovalRequest;
import com.dypiu.nba.entity.ApprovalStatus;
import com.dypiu.nba.entity.ApprovalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, String> {
    List<ApprovalRequest> findBySchoolId(String schoolId);
    List<ApprovalRequest> findByDepartmentId(String departmentId);
    List<ApprovalRequest> findByMasterProgrammeId(String masterProgrammeId);
    List<ApprovalRequest> findByProgrammeBatchId(String programmeBatchId);
    List<ApprovalRequest> findByProgrammeBatchCourseId(String programmeBatchCourseId);
    List<ApprovalRequest> findByStatus(ApprovalStatus status);
    List<ApprovalRequest> findByResourceId(String resourceId);
    List<ApprovalRequest> findByResourceIdIgnoreCase(String resourceId);

    @Query("SELECT a FROM ApprovalRequest a WHERE a.type IN (:types) " +
           "AND (LOWER(a.resourceId) = LOWER(:resId) " +
           "OR LOWER(a.resourceId) = LOWER(:altResId) " +
           "OR LOWER(a.masterProgrammeId) = LOWER(:progId) " +
           "OR (a.resourceId IS NOT NULL AND LOWER(a.resourceId) LIKE LOWER(CONCAT('%', :progId, '%')))) " +
           "ORDER BY COALESCE(a.updatedAt, a.approvedAt, a.submittedAt, a.createdAt) DESC")
    List<ApprovalRequest> findAllocationApprovals(@Param("types") Collection<ApprovalType> types,
                                                  @Param("resId") String resId,
                                                  @Param("altResId") String altResId,
                                                  @Param("progId") String progId);

    long countByTypeInAndMasterProgrammeIdInAndStatus(Collection<ApprovalType> types, Collection<String> masterProgrammeIds, ApprovalStatus status);

    long countByTypeAndMasterProgrammeIdInAndStatus(ApprovalType type, Collection<String> masterProgrammeIds, ApprovalStatus status);

    long countByTypeInAndProgrammeBatchCourseIdInAndStatus(Collection<ApprovalType> types, Collection<String> offeringIds, ApprovalStatus status);

    long countByTypeAndProgrammeBatchCourseIdInAndStatus(ApprovalType type, Collection<String> offeringIds, ApprovalStatus status);
}

