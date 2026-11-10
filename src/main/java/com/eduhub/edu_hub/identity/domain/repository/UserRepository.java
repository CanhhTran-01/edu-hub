package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    boolean existsByUsername(String username);

    @Query(
            "select new com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse(account.id, account.email) "
                    + "from User account where account.id in :userIds order by account.id")
    List<UserAccountEmailResponse> findAccountEmailsByUserIds(@Param("userIds") Set<Long> userIds);

    @Query("select account.id from User account where lower(trim(account.email)) = lower(trim(:email))")
    Set<Long> findUserIdsByEmail(@Param("email") String email);

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
    Optional<User> findByIdAndDeletedFalse(Long userId);

    @Override
    @EntityGraph(attributePaths = "role")
    Page<User> findAll(@NonNull Specification<User> specification, @NonNull Pageable pageable);

    long countByDeletedFalseAndStatusAndRole_RoleName(UserStatus status, String roleName);
}
