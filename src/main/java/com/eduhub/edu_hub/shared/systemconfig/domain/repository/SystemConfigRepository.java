package com.eduhub.edu_hub.shared.systemconfig.domain.repository;

import com.eduhub.edu_hub.shared.systemconfig.domain.model.SystemConfig;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    List<SystemConfig> findByDeletedFalseOrderByConfigKeyAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select configuration from SystemConfig configuration where configuration.deleted = false order by configuration.configKey")
    List<SystemConfig> findConfigurationsForUpdate();
}
