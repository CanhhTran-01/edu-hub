package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.domain.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = "role")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByUsernameAndDeletedFalse(String username);
}
