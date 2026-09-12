package com.dypiu.nba.repository;

import com.dypiu.nba.entity.MasterProgramme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasterProgrammeRepository extends JpaRepository<MasterProgramme, String> {
    List<MasterProgramme> findByDepartmentId(String departmentId);
    List<MasterProgramme> findByDepartmentIdIn(List<String> departmentIds);
    Optional<MasterProgramme> findByDegreeAwarded(String degreeAwarded);

    List<MasterProgramme> findByDeletedAtIsNull();
    List<MasterProgramme> findByDepartmentIdAndDeletedAtIsNull(String departmentId);
    List<MasterProgramme> findByDepartmentIdInAndDeletedAtIsNull(List<String> departmentIds);
    List<MasterProgramme> findByLevelAndDeletedAtIsNull(String level);
    List<MasterProgramme> findByDepartmentIdAndLevelAndDeletedAtIsNull(String departmentId, String level);
    Optional<MasterProgramme> findByDegreeAwardedAndDeletedAtIsNull(String degreeAwarded);
    Optional<MasterProgramme> findByIdAndDeletedAtIsNull(String id);
    List<MasterProgramme> findByIdInAndDeletedAtIsNull(Iterable<String> ids);
    boolean existsByDegreeAwardedAndDeletedAtIsNull(String degreeAwarded);
    boolean existsByIdAndDeletedAtIsNull(String id);

    default Optional<MasterProgramme> findByCode(String code) {
        return findByDegreeAwarded(code);
    }

    default Optional<MasterProgramme> findByCodeAndDeletedAtIsNull(String code) {
        return findByDegreeAwardedAndDeletedAtIsNull(code);
    }

    default boolean existsByCodeAndDeletedAtIsNull(String code) {
        return existsByDegreeAwardedAndDeletedAtIsNull(code);
    }

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(mp) > 0 FROM MasterProgramme mp JOIN Department d ON mp.departmentId = d.id WHERE d.schoolId = :schoolId AND LOWER(TRIM(mp.degreeAwarded)) = LOWER(TRIM(:degreeAwarded)) AND mp.deletedAt IS NULL AND mp.id != :excludeId")
    boolean existsByDegreeAwardedInSchoolExcludeId(@org.springframework.data.repository.query.Param("schoolId") String schoolId, @org.springframework.data.repository.query.Param("degreeAwarded") String degreeAwarded, @org.springframework.data.repository.query.Param("excludeId") String excludeId);

    default boolean existsByCodeInSchoolExcludeId(String schoolId, String code, String excludeId) {
        return existsByDegreeAwardedInSchoolExcludeId(schoolId, code, excludeId);
    }

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(mp) > 0 FROM MasterProgramme mp JOIN Department d ON mp.departmentId = d.id WHERE d.schoolId = :schoolId AND LOWER(TRIM(mp.name)) = LOWER(TRIM(:name)) AND mp.deletedAt IS NULL AND mp.id != :excludeId")
    boolean existsByNameInSchoolExcludeId(@org.springframework.data.repository.query.Param("schoolId") String schoolId, @org.springframework.data.repository.query.Param("name") String name, @org.springframework.data.repository.query.Param("excludeId") String excludeId);

    // Native queries to query trash if needed by developers (bypassing @SQLRestriction)
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM master_programmes WHERE deleted_at IS NOT NULL", nativeQuery = true)
    List<MasterProgramme> findAllTrashProgrammes();

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM master_programmes WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    Optional<MasterProgramme> findTrashProgrammeById(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE master_programmes SET deleted_at = NULL, deleted_by = NULL, status = 'ACTIVE' WHERE id = :id", nativeQuery = true)
    int restoreTrashProgrammeById(@org.springframework.data.repository.query.Param("id") String id);
}
