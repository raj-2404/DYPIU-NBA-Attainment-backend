package com.dypiu.nba.service;

import com.dypiu.nba.config.CacheConfig;
import com.dypiu.nba.entity.Department;
import com.dypiu.nba.entity.MasterProgramme;
import com.dypiu.nba.entity.ProgrammeBatch;
import com.dypiu.nba.entity.ProgrammeBatchCourse;
import com.dypiu.nba.repository.DepartmentRepository;
import com.dypiu.nba.repository.MasterProgrammeRepository;
import com.dypiu.nba.repository.ProgrammeBatchCourseRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicLookupCacheService {

    private final DepartmentRepository departmentRepository;
    private final MasterProgrammeRepository masterProgrammeRepository;
    private final ProgrammeBatchRepository programmeBatchRepository;
    private final ProgrammeBatchCourseRepository programmeBatchCourseRepository;

    @Cacheable(value = CacheConfig.CACHE_DEPARTMENTS, key = "#id", unless = "#result == null")
    public Department getDepartmentById(String id) {
        if (id == null || id.isBlank()) return null;
        return departmentRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = CacheConfig.CACHE_DEPARTMENTS, allEntries = true)
    public void evictDepartmentCache() {
        log.debug("[AcademicLookupCacheService] Evicted all department cache entries");
    }

    @Cacheable(value = CacheConfig.CACHE_MASTER_PROGRAMMES, key = "#id", unless = "#result == null")
    public MasterProgramme getMasterProgrammeById(String id) {
        if (id == null || id.isBlank()) return null;
        return masterProgrammeRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
    }

    @CacheEvict(value = CacheConfig.CACHE_MASTER_PROGRAMMES, allEntries = true)
    public void evictMasterProgrammeCache() {
        log.debug("[AcademicLookupCacheService] Evicted all master programme cache entries");
    }

    @Cacheable(value = CacheConfig.CACHE_PROGRAMME_BATCHES, key = "#id", unless = "#result == null")
    public ProgrammeBatch getProgrammeBatchById(String id) {
        if (id == null || id.isBlank()) return null;
        return programmeBatchRepository.findByIdAndDeletedAtIsNull(id)
                .or(() -> programmeBatchRepository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(id.trim()))
                .orElse(null);
    }

    @CacheEvict(value = CacheConfig.CACHE_PROGRAMME_BATCHES, allEntries = true)
    public void evictProgrammeBatchCache() {
        log.debug("[AcademicLookupCacheService] Evicted all programme batch cache entries");
    }

    @Cacheable(value = CacheConfig.CACHE_COURSES, key = "#id", unless = "#result == null")
    public ProgrammeBatchCourse getCourseOfferingById(String id) {
        if (id == null || id.isBlank()) return null;
        return programmeBatchCourseRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = CacheConfig.CACHE_COURSES, allEntries = true)
    public void evictCourseCache() {
        log.debug("[AcademicLookupCacheService] Evicted all course offering cache entries");
    }
}
