package com.dypiu.nba.repository;

import com.dypiu.nba.entity.ProgrammeBatchIndirectAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgrammeBatchIndirectAssessmentRepository extends JpaRepository<ProgrammeBatchIndirectAssessment, String> {

    List<ProgrammeBatchIndirectAssessment> findByProgrammeBatchIdOrderByCreatedAtAsc(String programmeBatchId);

    Optional<ProgrammeBatchIndirectAssessment> findByIdAndProgrammeBatchId(String id, String programmeBatchId);

    void deleteByProgrammeBatchId(String programmeBatchId);
}
