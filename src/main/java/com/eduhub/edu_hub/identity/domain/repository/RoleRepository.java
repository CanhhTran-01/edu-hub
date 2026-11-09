package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.domain.model.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleNameAndDeletedFalse(String roleName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from Role role where role.roleName = :roleName and role.deleted = false")
    Optional<Role> findByRoleNameForUpdate(@Param("roleName") String roleName);
}
