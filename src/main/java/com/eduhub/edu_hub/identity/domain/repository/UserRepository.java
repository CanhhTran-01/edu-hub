package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.username = :username")
    Optional<User> findByUsernameIncludingDeletedForUpdate(@Param("username") String username);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsernameAndDeletedFalse(String username);

    @EntityGraph(attributePaths = "role")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.username = :username and user.deleted = false")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @EntityGraph(attributePaths = "role")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId and user.deleted = false")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @EntityGraph(attributePaths = "role")
    Page<User> findByDeletedFalseAndUsernameContainingIgnoreCase(String username, Pageable pageable);

    long countByDeletedFalseAndStatusAndRole_RoleName(UserStatus status, String roleName);
}
