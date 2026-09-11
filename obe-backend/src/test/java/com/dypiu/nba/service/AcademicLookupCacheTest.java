package com.dypiu.nba.service;

import com.dypiu.nba.entity.Department;
import com.dypiu.nba.entity.MasterProgramme;
import com.dypiu.nba.entity.ProgrammeBatch;
import com.dypiu.nba.repository.DepartmentRepository;
import com.dypiu.nba.repository.MasterProgrammeRepository;
import com.dypiu.nba.repository.ProgrammeBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class AcademicLookupCacheTest {

    @Autowired
    private AcademicLookupCacheService cacheService;

    @MockBean
    private DepartmentRepository departmentRepository;

    @MockBean
    private MasterProgrammeRepository masterProgrammeRepository;

    @MockBean
    private ProgrammeBatchRepository programmeBatchRepository;

    @Test
    @DisplayName("Verify Department L1 Cache Hit and Eviction")
    void testDepartmentCachingAndEviction() {
        Department dept = Department.builder().id("dept-cache-1").name("CSE").schoolId("sch-1").build();
        when(departmentRepository.findById("dept-cache-1")).thenReturn(Optional.of(dept));

        // First call -> Cache Miss (repo called)
        Department first = cacheService.getDepartmentById("dept-cache-1");
        assertNotNull(first);
        assertEquals("CSE", first.getName());
        verify(departmentRepository, times(1)).findById("dept-cache-1");

        // Second call -> Cache Hit (repo NOT called again)
        Department second = cacheService.getDepartmentById("dept-cache-1");
        assertNotNull(second);
        assertEquals("CSE", second.getName());
        verify(departmentRepository, times(1)).findById("dept-cache-1");

        // Evict cache
        cacheService.evictDepartmentCache();

        // Third call -> Cache Miss after eviction (repo called 2nd time)
        Department third = cacheService.getDepartmentById("dept-cache-1");
        assertNotNull(third);
        verify(departmentRepository, times(2)).findById("dept-cache-1");
    }

    @Test
    @DisplayName("Verify MasterProgramme L1 Cache Hit and Eviction")
    void testMasterProgrammeCachingAndEviction() {
        MasterProgramme prog = MasterProgramme.builder().id("prog-cache-1").name("B.Tech CSE").departmentId("dept-1").build();
        when(masterProgrammeRepository.findByIdAndDeletedAtIsNull("prog-cache-1")).thenReturn(Optional.of(prog));

        // First call -> Cache Miss
        MasterProgramme first = cacheService.getMasterProgrammeById("prog-cache-1");
        assertNotNull(first);
        verify(masterProgrammeRepository, times(1)).findByIdAndDeletedAtIsNull("prog-cache-1");

        // Second call -> Cache Hit
        MasterProgramme second = cacheService.getMasterProgrammeById("prog-cache-1");
        assertNotNull(second);
        verify(masterProgrammeRepository, times(1)).findByIdAndDeletedAtIsNull("prog-cache-1");

        // Evict cache
        cacheService.evictMasterProgrammeCache();

        // Third call -> Cache Miss after eviction
        MasterProgramme third = cacheService.getMasterProgrammeById("prog-cache-1");
        assertNotNull(third);
        verify(masterProgrammeRepository, times(2)).findByIdAndDeletedAtIsNull("prog-cache-1");
    }
}
