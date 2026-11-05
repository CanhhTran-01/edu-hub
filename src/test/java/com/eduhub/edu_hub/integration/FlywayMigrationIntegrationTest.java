package com.eduhub.edu_hub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayMigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:18");

    private final JdbcTemplate jdbc = new JdbcTemplate(
            new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));

    @Test
    void schemaOnlyIncludesRequiredRolesButNoDevelopmentFixtures() {
        String schema = "schema_only";
        Flyway flyway = flyway(schema, "classpath:db/migration");

        flyway.migrate();

        assertThat(count(schema, "roles")).isEqualTo(4);
        for (String table : new String[] {
            "users", "subjects", "majors", "curriculums", "curriculum_subjects",
            "semesters", "faculties", "lecturers", "cohorts", "administrative_classes",
            "students", "module_classes", "people_user_links"
        }) {
            assertThat(count(schema, table))
                    .as(table + " must not contain development fixtures")
                    .isZero();
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_schema = ? "
                                + "and table_name = 'administrative_classes' "
                                + "and column_name in ('major_code', 'major_name')",
                        Integer.class,
                        schema))
                .isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void developmentSeedsUseFinalSchemaAndAreAppliedOnlyOnce() {
        String schema = "development";
        Flyway flyway = flyway(schema, "classpath:db/migration", "classpath:db/seed");

        flyway.migrate();

        assertThat(count(schema, "roles")).isEqualTo(4);
        assertThat(count(schema, "users")).isEqualTo(4);
        assertThat(count(schema, "subjects")).isEqualTo(124);
        assertThat(count(schema, "majors")).isEqualTo(4);
        assertThat(count(schema, "curriculums")).isEqualTo(4);
        assertThat(count(schema, "curriculum_subjects")).isEqualTo(160);
        assertThat(count(schema, "semesters")).isEqualTo(12);
        assertThat(count(schema, "faculties")).isEqualTo(2);
        assertThat(count(schema, "lecturers")).isEqualTo(3);
        assertThat(count(schema, "cohorts")).isEqualTo(1);
        assertThat(count(schema, "administrative_classes")).isEqualTo(2);
        assertThat(count(schema, "students")).isZero();
        assertThat(count(schema, "module_classes")).isZero();
        assertThat(jdbc.queryForList(
                        "select required_graduation_credits from development.curriculums " + "order by curriculum_code",
                        Integer.class))
                .containsExactly(123, 124, 123, 124);
        assertThat(jdbc.queryForObject(
                        "select count(*) from development.curriculums c "
                                + "where c.required_graduation_credits <> ("
                                + "select sum(s.credits) from development.curriculum_subjects cs "
                                + "join development.subjects s on s.id = cs.subject_id "
                                + "where cs.curriculum_id = c.id and cs.subject_type = 'REQUIRED')",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForList(
                        "select class_name from development.administrative_classes order by class_name", String.class))
                .containsExactly("L01", "L02");

        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(count(schema, "users")).isEqualTo(4);
        assertThat(count(schema, "curriculum_subjects")).isEqualTo(160);
    }

    private Flyway flyway(String schema, String... locations) {
        return Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations(locations)
                .load();
    }

    private int count(String schema, String table) {
        return jdbc.queryForObject("select count(*) from " + schema + "." + table, Integer.class);
    }
}
