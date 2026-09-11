package com.dypiu.nba.service;

import com.dypiu.nba.entity.School;
import com.dypiu.nba.entity.User;
import com.dypiu.nba.entity.UserRole;
import com.dypiu.nba.repository.SchoolRepository;
import com.dypiu.nba.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dypiu.nba.entity.MasterProgramme;
import com.dypiu.nba.entity.Department;
import com.dypiu.nba.repository.DepartmentRepository;
import com.dypiu.nba.repository.MasterProgrammeRepository;

@ExtendWith(MockitoExtension.class)
@WithMockUser(roles = "IQAC")
public class SchoolDirectorMappingTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MasterProgrammeRepository masterProgrammeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AcademicLookupCacheService academicLookupCacheService;

    @InjectMocks
    private AcademicService academicService;

    @Test
    @DisplayName("Successfully create school when director is not already mapped")
    void testSaveSchool_Success() {
        School newSchool = School.builder()
                .code("SOE")
                .name("School of Engineering")
                .directorId(10L)
                .directorEmail("director.soe@dypiu.ac.in")
                .directorName("Dr. Director SOE")
                .build();

        User directorUser = User.builder()
                .id(10L)
                .name("Dr. Director SOE")
                .email("director.soe@dypiu.ac.in")
                .role(UserRole.DIRECTOR)
                .build();

        when(schoolRepository.findByDirectorId(10L)).thenReturn(Optional.empty());
        when(schoolRepository.findByDirectorEmailIgnoreCase("director.soe@dypiu.ac.in")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("director.soe@dypiu.ac.in")).thenReturn(Optional.of(directorUser));
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));

        School saved = academicService.saveSchool(newSchool);

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("SOE", saved.getCode());
        assertEquals(10L, saved.getDirectorId());
        assertEquals("director.soe@dypiu.ac.in", saved.getDirectorEmail());
        verify(userRepository, atLeastOnce()).save(directorUser);
        assertEquals(saved.getId(), directorUser.getSchoolId());
    }

    @Test
    @DisplayName("Fail to create school when director email is already mapped to another school")
    void testSaveSchool_DuplicateDirectorEmail_ThrowsException() {
        School existingSchool = School.builder()
                .id("sch-soe")
                .code("SOE")
                .name("School of Engineering")
                .directorId(10L)
                .directorEmail("director@dypiu.ac.in")
                .build();

        School duplicateSchool = School.builder()
                .code("SOM")
                .name("School of Management")
                .directorEmail("director@dypiu.ac.in")
                .build();

        when(schoolRepository.findByDirectorEmailIgnoreCase("director@dypiu.ac.in"))
                .thenReturn(Optional.of(existingSchool));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            academicService.saveSchool(duplicateSchool);
        });

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already assigned to School: School of Engineering"));
        verify(schoolRepository, never()).save(duplicateSchool);
    }

    @Test
    @DisplayName("Fail to update school when assigning a director already mapped to a different school")
    void testUpdateSchool_DuplicateDirector_ThrowsException() {
        School currentSchool = School.builder()
                .id("sch-som")
                .code("SOM")
                .name("School of Management")
                .build();

        School otherSchool = School.builder()
                .id("sch-soe")
                .code("SOE")
                .name("School of Engineering")
                .directorId(10L)
                .directorEmail("director.soe@dypiu.ac.in")
                .build();

        School updateDetails = School.builder()
                .directorId(10L)
                .directorEmail("director.soe@dypiu.ac.in")
                .build();

        when(schoolRepository.findById("sch-som")).thenReturn(Optional.of(currentSchool));
        when(schoolRepository.findByDirectorId(10L)).thenReturn(Optional.of(otherSchool));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            academicService.updateSchool("sch-som", updateDetails);
        });

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Director is already assigned to School: School of Engineering"));
    }

    @Test
    @DisplayName("Successfully save programme preserving coordinator and coordinatorEmail")
    void testSaveProgramme_PreservesCoordinatorAndEmail() {
        MasterProgramme inputProg = MasterProgramme.builder()
                .id("prog-1a1b6c2e")
                .name("B.Tech Computer science")
                .code("BTCS")
                .departmentId("dept-cs")
                .coordinator("prag")
                .coordinatorEmail("pc@gmail.com")
                .build();

        User pcUser = User.builder()
                .id(25L)
                .name("Prag PC")
                .email("pc@gmail.com")
                .role(UserRole.FACULTY)
                .build();

        
        Department mockDept = Department.builder().id("dept-cs").schoolId("school-1").build();
        when(departmentRepository.findById("dept-cs")).thenReturn(Optional.of(mockDept));
        when(masterProgrammeRepository.findByIdAndDeletedAtIsNull("prog-1a1b6c2e")).thenReturn(Optional.of(inputProg));
        when(userRepository.findByEmail("pc@gmail.com")).thenReturn(Optional.of(pcUser));
        when(masterProgrammeRepository.save(any(MasterProgramme.class))).thenAnswer(inv -> inv.getArgument(0));

        MasterProgramme saved = academicService.saveProgramme(inputProg);

        assertNotNull(saved);
        assertEquals("prog-1a1b6c2e", saved.getId());
        assertEquals("Prag PC", saved.getCoordinator());
        assertEquals("pc@gmail.com", saved.getCoordinatorEmail());
        verify(userRepository).save(pcUser);
        assertEquals(UserRole.PROGRAMME_COORDINATOR, pcUser.getRole());
        assertEquals("prog-1a1b6c2e", pcUser.getMasterProgrammeId());
    }
}
