package com.dypiu.nba.repository;

import com.dypiu.nba.entity.ProgrammeBatchCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgrammeBatchCourseRepository extends JpaRepository<ProgrammeBatchCourse, String> {
    List<ProgrammeBatchCourse> findByProgrammeBatchId(String programmeBatchId);
    List<ProgrammeBatchCourse> findByProgrammeBatchIdIn(Collection<String> programmeBatchIds);
    Optional<ProgrammeBatchCourse> findByIdAndDeletedAtIsNull(String id);
    List<ProgrammeBatchCourse> findByDeletedAtIsNull();
    List<ProgrammeBatchCourse> findByProgrammeBatchIdAndDeletedAtIsNull(String programmeBatchId);
    List<ProgrammeBatchCourse> findByProgrammeBatchIdInAndDeletedAtIsNull(Collection<String> programmeBatchIds);

    default List<ProgrammeBatchCourse> findByMasterCourseId(String masterCourseId) {
        if (masterCourseId == null) return List.of();
        return findById(masterCourseId).map(List::of).orElseGet(List::of);
    }

    default List<ProgrammeBatchCourse> findByProgrammeBatchIdAndMasterCourseId(String programmeBatchId, String masterCourseId) {
        if (masterCourseId == null) return List.of();
        return findById(masterCourseId)
                .filter(c -> programmeBatchId != null && programmeBatchId.equalsIgnoreCase(c.getProgrammeBatchId()))
                .map(List::of).orElseGet(List::of);
    }

    default List<ProgrammeBatchCourse> findByMasterCourseIdAndDeletedAtIsNull(String masterCourseId) {
        if (masterCourseId == null) return List.of();
        return findByIdAndDeletedAtIsNull(masterCourseId).map(List::of).orElseGet(List::of);
    }

    default List<ProgrammeBatchCourse> findByProgrammeBatchIdAndMasterCourseIdAndDeletedAtIsNull(String programmeBatchId, String masterCourseId) {
        if (masterCourseId == null) return List.of();
        return findByIdAndDeletedAtIsNull(masterCourseId)
                .filter(c -> programmeBatchId != null && programmeBatchId.equalsIgnoreCase(c.getProgrammeBatchId()))
                .map(List::of).orElseGet(List::of);
    }

    default Optional<ProgrammeBatchCourse> findFirstByProgrammeBatchIdAndMasterCourseId(String programmeBatchId, String masterCourseId) {
        if (masterCourseId == null) return Optional.empty();
        return findById(masterCourseId)
                .filter(c -> programmeBatchId != null && programmeBatchId.equalsIgnoreCase(c.getProgrammeBatchId()));
    }

    default boolean existsByProgrammeBatchIdAndMasterCourseIdAndDeletedAtIsNull(String programmeBatchId, String masterCourseId) {
        if (masterCourseId == null) return false;
        return findByIdAndDeletedAtIsNull(masterCourseId)
                .filter(c -> programmeBatchId != null && programmeBatchId.equalsIgnoreCase(c.getProgrammeBatchId()))
                .isPresent();
    }

    default boolean existsByProgrammeBatchIdAndMasterCourseIdAndIdNotAndDeletedAtIsNull(String programmeBatchId, String masterCourseId, String id) {
        if (masterCourseId == null) return false;
        return findByIdAndDeletedAtIsNull(masterCourseId)
                .filter(c -> !c.getId().equals(id) && programmeBatchId != null && programmeBatchId.equalsIgnoreCase(c.getProgrammeBatchId()))
                .isPresent();
    }
    default boolean existsByProgrammeBatchIdAndMasterCourseId(String programmeBatchId, String masterCourseId) {
        return existsByProgrammeBatchIdAndMasterCourseIdAndDeletedAtIsNull(programmeBatchId, masterCourseId);
    }
    default boolean existsByProgrammeBatchIdAndMasterCourseIdAndIdNot(String programmeBatchId, String masterCourseId, String id) {
        return existsByProgrammeBatchIdAndMasterCourseIdAndIdNotAndDeletedAtIsNull(programmeBatchId, masterCourseId, id);
    }
    List<ProgrammeBatchCourse> findByCourseCoordinatorId(Long courseCoordinatorId);
    List<ProgrammeBatchCourse> findByCourseCoordinatorNameContainingIgnoreCaseOrAssignedFacultyContainingIgnoreCase(String name, String faculty);
    List<ProgrammeBatchCourse> findByProgrammeBatchIdAndStatus(String programmeBatchId, String status);

    Optional<ProgrammeBatchCourse> findByProgrammeBatchIdAndCodeAndDeletedAtIsNull(String programmeBatchId, String code);
    Optional<ProgrammeBatchCourse> findFirstByProgrammeBatchIdAndCodeIgnoreCaseAndDeletedAtIsNull(String programmeBatchId, String code);
    boolean existsByProgrammeBatchIdAndCodeIgnoreCaseAndDeletedAtIsNull(String programmeBatchId, String code);
    boolean existsByProgrammeBatchIdAndCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String programmeBatchId, String code, String id);

    List<ProgrammeBatchCourse> findByCode(String code);
    List<ProgrammeBatchCourse> findByCodeIgnoreCase(String code);

    // Native queries to query trash if needed by developers (bypassing @SQLRestriction)
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM programme_batch_courses WHERE deleted_at IS NOT NULL", nativeQuery = true)
    List<ProgrammeBatchCourse> findAllTrashCourses();

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM programme_batch_courses WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    Optional<ProgrammeBatchCourse> findTrashCourseById(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM programme_batch_courses WHERE programme_batch_id = :programmeBatchId AND LOWER(code) = LOWER(:code) AND deleted_at IS NOT NULL LIMIT 1", nativeQuery = true)
    Optional<ProgrammeBatchCourse> findTrashCourseByProgrammeBatchIdAndCodeIgnoreCase(@org.springframework.data.repository.query.Param("programmeBatchId") String programmeBatchId, @org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE programme_batch_courses SET deleted_at = NULL, deleted_by = NULL, status = 'ACTIVE' WHERE id = :id", nativeQuery = true)
    int restoreTrashCourseById(@org.springframework.data.repository.query.Param("id") String id);
}
