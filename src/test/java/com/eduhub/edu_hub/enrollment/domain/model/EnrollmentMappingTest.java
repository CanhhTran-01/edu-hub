package com.eduhub.edu_hub.enrollment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class EnrollmentMappingTest {
    @Test
    void mapsEveryEnrollmentColumnToAnExistingMigrationColumn() throws Exception {
        // Chỉ dựng metadata để kiểm tra tên cột thực tế; không mở SessionFactory, Spring context hoặc kết nối DB.
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .build();
        try (var migration = getClass()
                .getResourceAsStream("/db/migration/V20261113055035__implement_module_class_enrollment.sql")) {
            assertThat(migration).isNotNull();
            String migrationSql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Enrollment.class)
                    .getMetadataBuilder()
                    .applyPhysicalNamingStrategy(new PhysicalNamingStrategySnakeCaseImpl())
                    .build();
            var table = metadata.getEntityBinding(Enrollment.class.getName()).getTable();
            assertThat(table.getName()).isEqualTo("enrollments");
            assertThat(table.getColumns()).isNotEmpty().allSatisfy(column -> assertThat(migrationSql)
                    .as("Cột được Hibernate ánh xạ: %s", column.getName())
                    .containsPattern("(?m)^\\s+" + Pattern.quote(column.getName()) + "\\s"));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
