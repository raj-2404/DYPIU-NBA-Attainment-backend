package com.dypiu.nba.repository;

import com.dypiu.nba.entity.User;
import com.dypiu.nba.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByUsernameOrEmail(String username, String email);
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    List<User> findByRole(UserRole role);
    List<User> findBySchoolId(String schoolId);
    List<User> findByRoleAndSchoolId(UserRole role, String schoolId);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE is_active = false", nativeQuery = true)
    List<User> findDeactivatedUsers();

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE id = :id AND is_active = false", nativeQuery = true)
    Optional<User> findDeactivatedUserById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE users SET is_active = true WHERE id = :id", nativeQuery = true)
    int reactivateUserById(@org.springframework.data.repository.query.Param("id") Long id);
}
