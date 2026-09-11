package com.dypiu.nba.repository;

import com.dypiu.nba.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, String> {
    List<Student> findByProgrammeBatchId(String programmeBatchId);
    Optional<Student> findByPrn(String prn);
    List<Student> findByPrnIn(java.util.Collection<String> prns);
    Optional<Student> findByProgrammeBatchIdAndPrn(String programmeBatchId, String prn);
}
