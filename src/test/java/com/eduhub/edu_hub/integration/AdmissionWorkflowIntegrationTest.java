package com.eduhub.edu_hub.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.*;
import com.eduhub.edu_hub.admission.application.exception.*;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.*;
import com.eduhub.edu_hub.admission.application.service.*;
import com.eduhub.edu_hub.admission.infrastructure.spreadsheet.*;
import com.eduhub.edu_hub.notification.application.command.QueueAdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import com.eduhub.edu_hub.notification.infrastructure.dispatch.AdmissionNotificationDispatcher;
import com.eduhub.edu_hub.notification.infrastructure.mail.SmtpEmailSender;
import com.eduhub.edu_hub.notification.infrastructure.template.EmailTemplateRenderer;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import com.eduhub.edu_hub.support.PostgresIntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(
        properties = {
            "admission.notifications-enabled=false",
            "spring.flyway.default-schema=admission_workflow",
            "spring.flyway.schemas=admission_workflow",
            "spring.jpa.properties.hibernate.default_schema=admission_workflow",
            "spring.datasource.hikari.schema=admission_workflow"
        })
class AdmissionWorkflowIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    AdmissionPlanningService planning;

    @Autowired
    AdmissionClassPreparationService classes;

    @Autowired
    AdmissionImportService imports;

    @Autowired
    AdmissionAllocationService allocation;

    @Autowired
    AdmissionConfirmationService confirmation;

    @Autowired
    QueuedAdmissionNotificationSender sender;

    @Autowired
    AdmissionNotificationQueryService notices;

    @Autowired
    AdmissionNotificationDispatcher dispatcher;

    @Autowired
    NotificationFacade.AdmissionQueue notificationQueue;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    com.eduhub.edu_hub.people.application.code.StudentCodeGenerator studentCodes;

    @MockitoBean
    SmtpEmailSender mail;

    @MockitoSpyBean
    EmailTemplateRenderer templates;

    private Long majorId;
    private Long cohortId;

    @BeforeEach
    void scope() {
        majorId = jdbc.queryForObject("select id from majors where major_code='CNTT' and not is_deleted", Long.class);
        cohortId = jdbc.queryForObject("select id from cohorts where cohort_code='CT8' and not is_deleted", Long.class);
    }

    @Test
    void separatesPlanningClassCreationAndConfirmationAndAllocatesCodesByGivenName() throws Exception {
        long before = count("students");
        PlanResult plan = imported(
                2,
                3,
                1,
                candidate("FLOW-A", "910000000001", "Trần Văn Bình", "30", null),
                candidate("FLOW-B", "910000000002", "Nguyễn Văn An", "29", null),
                candidate("FLOW-C", "910000000003", "Lê Văn Cường", "28", null),
                candidate("FLOW-D", "910000000004", "Phạm Văn Dũng", "27", null));
        assertThat(plan.state()).isEqualTo("DRAFT");
        assertThat(plan.scenarios().getFirst().classCount()).isEqualTo(2);
        assertThat(plan.classes()).isEmpty();
        assertThat(count("students")).isEqualTo(before);
        plan = prepared(plan, 2);
        assertThat(plan.classes()).hasSize(2);
        assertThat(count("students")).isEqualTo(before);
        plan = allocation.previewAllocation(plan.id(), new PreviewAllocationCommand(plan.revision()));
        assertThat(plan.averageScoreDifference()).isEqualByComparingTo("0");
        assertThat(count("students")).isEqualTo(before);
        var result = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.enrolled()).isEqualTo(4);
        assertThat(result.failed()).isZero();
        for (ClassResult target : plan.classes()) {
            var assigned = new ArrayList<>(result.applicants().stream()
                    .filter(row -> target.id().equals(row.assignedClassId()))
                    .toList());
            assigned.sort(Comparator.comparing(ApplicantResult::fullName, TextsNormalizer.vietnameseNameOrder()));
            assertThat(assigned.getFirst().studentCode()).endsWith("01").startsWith("CNTT08");
            assertThat(assigned.getLast().studentCode()).endsWith("02");
        }
        Long userId = jdbc.queryForObject("select user_id from students where citizen_id='910000000001'", Long.class);
        String hash = jdbc.queryForObject("select password_hash from users where id=?", String.class, userId);
        assertThat(new BCryptPasswordEncoder().matches("12345", hash)).isTrue();
        assertThat(jdbc.queryForObject("select is_first_login from users where id=?", Boolean.class, userId))
                .isTrue();
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(notices.listPlanNotifications(plan.id()))
                .allMatch(row -> row.state().equals("SKIPPED") && row.failureCount() == 0);
        assertThat(confirmation
                        .confirmEnrollment(plan.id(), new ConfirmEnrollmentCommand(0, false, false, null))
                        .enrolled())
                .isEqualTo(4);
        assertThat(count("students")).isEqualTo(before + 4);
        verifyNoInteractions(mail);
    }

    @Test
    void acceptsANonRecommendedNormalScenarioWithoutAutoSelectingFewestClasses() {
        var plan = planning.createPlan(new CreatePlanCommand(majorId, cohortId, 200, 40, 50, 15));
        plan = prepared(plan, 5);
        assertThat(plan.selectedClassCount()).isEqualTo(5);
        assertThat(plan.classes()).extracting(ClassResult::targetSize).containsExactly(40, 40, 40, 40, 40);
    }

    @Test
    void persistsInvalidAndConflictingRowsWhileContinuingWithValidRows() throws Exception {
        var plan = imported(
                1,
                5,
                0,
                candidate("VALID-A", "920000000001", "Nguyễn Văn An", "25", null),
                candidate("INVALID-A", "123", "Trần Văn Bình", "24", null),
                candidate("CONFLICT-A", "920000000003", "Lê Văn Cường", "23", null),
                candidate("CONFLICT-A", "920000000004", "Phạm Văn Dũng", "22", null));
        assertThat(plan.totalStudents()).isEqualTo(1);
        assertThat(plan.applicants())
                .extracting(ApplicantResult::state)
                .containsExactly("VALID", "INVALID", "CONFLICT", "CONFLICT");
        plan = allocation.previewAllocation(prepared(plan, 1).id(), new PreviewAllocationCommand(plan.revision() + 1));
        assertThat(confirmation
                        .confirmEnrollment(plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null))
                        .enrolled())
                .isEqualTo(1);
    }

    @Test
    void reusesLockedAccountAndDistinguishesIdenticalImportFromChangedApplication() throws Exception {
        var original = ready(1, 4, 0, candidate("REUSE-OLD", "930000000001", "Nguyễn Văn An", "25", null));
        var originalResult = confirmation.confirmEnrollment(
                original.id(), new ConfirmEnrollmentCommand(original.revision(), false, false, null));
        var person = originalResult.applicants().getFirst();
        Long userId = jdbc.queryForObject("select user_id from students where id=?", Long.class, person.studentId());
        String changedPassword = new BCryptPasswordEncoder().encode("existing-password");
        jdbc.update(
                "update users set password_hash=?, is_first_login=false, status='LOCKED' where id=?",
                changedPassword,
                userId);
        var repeat = imported(1, 4, 0, candidate("REUSE-OLD", "930000000001", "Nguyễn Văn An", "25", null));
        assertThat(repeat.totalStudents()).isZero();
        assertThat(repeat.applicants().getFirst().state()).isEqualTo("EXISTING");
        var conflict = imported(1, 4, 0, candidate("REUSE-OLD", "930000000001", "Nguyễn Văn An", "26", null));
        assertThat(conflict.applicants().getFirst().state()).isEqualTo("CONFLICT");
        var differentPerson = imported(1, 4, 0, candidate("REUSE-OLD", "930000000002", "Nguyễn Văn An", "25", null));
        assertThat(differentPerson.applicants().getFirst().state()).isEqualTo("CONFLICT");

        long before = count("students");
        var next = ready(1, 4, 0, candidate("REUSE-NEW", "930000000001", "Nguyễn Văn An", "27", "reuse@example.com"));
        assertThat(next.totalStudents()).isEqualTo(1);
        assertThat(next.applicants().getFirst().warnings()).isNotEmpty();
        var result = confirmation.confirmEnrollment(
                next.id(), new ConfirmEnrollmentCommand(next.revision(), false, false, null));
        assertThat(result.enrolled()).isEqualTo(1);
        assertThat(result.applicants().getFirst().studentId()).isEqualTo(person.studentId());
        assertThat(result.applicants().getFirst().studentCode()).isEqualTo(person.studentCode());
        assertThat(count("students")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select password_hash from users where id=?", String.class, userId))
                .isEqualTo(changedPassword);
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, userId))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject("select is_first_login from users where id=?", Boolean.class, userId))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from student_admissions where student_id=?",
                        Integer.class,
                        person.studentId()))
                .isEqualTo(2);
        var notice = notices.listPlanNotifications(next.id()).getFirst();
        sender.sendScheduledAttempt(notice.id());
        verify(mail)
                .sendEmail(
                        eq("reuse@example.com"),
                        argThat(email -> email.subject().contains("Xác nhận nhập học")
                                && email.textBody().contains(person.studentCode())
                                && !email.textBody().contains("12345")));
        verifyNoMoreInteractions(mail);
    }

    @Test
    void allowsTwoSameNamesWithoutWarningAndRequiresReasonForUnderMinimum() throws Exception {
        var plan = imported(
                3,
                4,
                0,
                candidate("NAME-A", "940000000001", "Nguyễn Văn An", "25", null),
                candidate("NAME-B", "940000000002", "Nguyễn Văn An", "24", null));
        assertThatThrownBy(() -> classes.prepareClasses(
                        new PrepareClassesCommand(plan.id(), plan.revision(), 1, List.of(), false, true, null)))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);
        var selected = classes.prepareClasses(new PrepareClassesCommand(
                plan.id(), plan.revision(), 1, List.of(), false, true, "Nhóm tuyển sinh nhỏ"));
        selected = allocation.previewAllocation(selected.id(), new PreviewAllocationCommand(selected.revision()));
        assertThat(selected.warnings()).noneMatch(value -> value.contains("trùng họ tên"));
        var response = confirmation.confirmEnrollment(
                selected.id(), new ConfirmEnrollmentCommand(selected.revision(), false, true, "Nhóm tuyển sinh nhỏ"));
        assertThat(response.enrolled()).isEqualTo(2);
        assertThat(response.state()).isEqualTo("CONFIRMED");
    }

    @Test
    void warnsOnceForFourSameNamesButStillAllowsConfirmation() throws Exception {
        var plan = ready(
                1,
                5,
                0,
                candidate("NAME-C", "940000000003", "Nguyễn Văn An", "25", null),
                candidate("NAME-D", "940000000004", "Nguyễn Văn An", "24", null),
                candidate("NAME-E", "940000000005", "Nguyễn Văn An", "23", null),
                candidate("NAME-F", "940000000006", "Nguyễn Văn An", "22", null));
        assertThat(plan.warnings().stream()
                        .filter(value -> value.contains("trùng họ tên"))
                        .toList())
                .singleElement()
                .satisfies(value -> assertThat(value).contains("4 sinh viên", "(tối đa 3)"));
        var result = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.enrolled()).isEqualTo(4);
        assertThat(result.failed()).isZero();
    }

    @Test
    void retriesFailedRowsWithoutRollingBackSuccessfulRowsOrDuplicatingIdentity() throws Exception {
        var old = ready(1, 3, 0, candidate("PARTIAL-OLD", "950000000001", "Nguyễn Văn An", "25", null));
        var first = confirmation.confirmEnrollment(
                old.id(), new ConfirmEnrollmentCommand(old.revision(), false, false, null));
        var plan = ready(
                1,
                3,
                0,
                candidate("PARTIAL-REUSE", "950000000001", "Nguyễn Văn An", "25", null),
                candidate("PARTIAL-NEW", "950000000002", "Trần Văn Bình", "24", null));
        try {
            jdbc.update("update roles set is_deleted=true where role_name='STUDENT'");
            var partial = confirmation.confirmEnrollment(
                    plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
            assertThat(partial.enrolled()).isEqualTo(1);
            assertThat(partial.failed()).isEqualTo(1);
            assertThat(partial.state()).isEqualTo("PARTIALLY_CONFIRMED");
            assertThat(partial.applicants().stream()
                            .filter(row -> row.applicationCode().equals("PARTIAL-REUSE"))
                            .findFirst()
                            .orElseThrow()
                            .studentId())
                    .isEqualTo(first.applicants().getFirst().studentId());
            assertThat(jdbc.queryForObject(
                            "select count(*) from students where citizen_id='950000000002'", Integer.class))
                    .isZero();
            jdbc.update("update roles set is_deleted=false where role_name='STUDENT'");
            var completed = confirmation.confirmEnrollment(
                    plan.id(), new ConfirmEnrollmentCommand(partial.revision(), false, false, null));
            assertThat(completed.enrolled()).isEqualTo(2);
            assertThat(completed.failed()).isZero();
            assertThat(completed.state()).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject(
                            "select count(*) from students where citizen_id='950000000002'", Integer.class))
                    .isEqualTo(1);
        } finally {
            jdbc.update("update roles set is_deleted=false where role_name='STUDENT'");
        }
    }

    @Test
    void stopsAfterInitialFailureAndThreeScheduledRetriesWithoutManualReset() throws Exception {
        var plan = ready(1, 3, 0, candidate("MAIL-FAIL", "960000000001", "Nguyễn Văn An", "25", "failed@example.com"));
        confirmation.confirmEnrollment(plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        var notification = notices.listPlanNotifications(plan.id()).getFirst();
        doThrow(new MailSendException("unavailable"))
                .when(mail)
                .sendEmail(eq("failed@example.com"), any(RenderedEmail.class));
        sender.sendInitialAttempt(notification.id());
        assertThat(notices.listPlanNotifications(plan.id()).getFirst().failureCount())
                .isEqualTo(1);
        jdbc.update(
                "update admission_notifications set next_attempt_at=timestamp '2000-01-01' where id=?",
                notification.id());
        sender.sendInitialAttempt(notification.id()); // A delayed async kickoff must not become a retry.
        assertThat(notices.listPlanNotifications(plan.id()).getFirst().failureCount())
                .isEqualTo(1);
        org.springframework.test.util.ReflectionTestUtils.setField(dispatcher, "admissionNotificationsEnabled", true);
        try {
            for (int i = 0; i < 4; i++) {
                jdbc.update(
                        "update admission_notifications set next_attempt_at=timestamp '2000-01-01' where id=?",
                        notification.id());
                dispatcher.sendDueNotifications(); // The fourth poll must leave the terminal notification untouched.
            }
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    dispatcher, "admissionNotificationsEnabled", false);
        }
        var failed = notices.listPlanNotifications(plan.id()).getFirst();
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.failureCount()).isEqualTo(4);
        sender.sendScheduledAttempt(notification.id());
        sender.sendInitialAttempt(notification.id());
        verify(mail, times(4))
                .sendEmail(
                        eq("failed@example.com"),
                        argThat(email -> email.textBody().contains("12345")
                                && email.htmlBody().contains("12345")));
        assertThat(jdbc.queryForObject("select count(*) from students where citizen_id='960000000001'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsStaleRequestsAndManualMovesBeyondHardCapacity() throws Exception {
        var plan = ready(
                1,
                2,
                0,
                candidate("HARD-A", "970000000001", "Nguyễn Văn An", "30", null),
                candidate("HARD-B", "970000000002", "Trần Văn Bình", "29", null),
                candidate("HARD-C", "970000000003", "Lê Văn Cường", "28", null),
                candidate("HARD-D", "970000000004", "Phạm Văn Dũng", "27", null));
        var target = plan.classes().getFirst();
        var other = plan.applicants().stream()
                .filter(row -> !target.id().equals(row.assignedClassId()))
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> allocation.adjustAssignments(
                        plan.id(),
                        new AdjustAssignmentsCommand(
                                plan.revision(), List.of(new AssignmentCommand(other.id(), target.id())))))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);
        assertThatThrownBy(() -> allocation.adjustAssignments(
                        plan.id(),
                        new AdjustAssignmentsCommand(0, List.of(new AssignmentCommand(other.id(), target.id())))))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);
    }

    @Test
    void importsCorrectedErrorsAfterCompletionWithoutMovingOrRenumberingEnrolledStudents() throws Exception {
        var plan = ready(
                1,
                3,
                1,
                candidate("CORRECT-A", "980000000001", "Nguyễn Văn An", "30", null),
                candidate("CORRECT-B", "980000000002", "Trần Văn Bình", "29", null),
                candidate("CORRECT-C", "1", "Lê Văn Cường", "28", null),
                candidate("CORRECT-D", "2", "Phạm Văn Dũng", "27", null));
        var first = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        assertThat(first.enrolled()).isEqualTo(2);
        var unchanged = first.applicants().stream()
                .filter(row -> row.state().equals("ENROLLED"))
                .collect(java.util.stream.Collectors.toMap(
                        ApplicantResult::applicationCode, ApplicantResult::studentCode));
        plan = imports.importApplicants(plan.id(), first.revision(), spreadsheet(new String[][] {
            candidate("CORRECT-C", "980000000003", "Lê Văn Cường", "28", null),
            candidate("CORRECT-C", "980000000003", "Lê Văn Cường", "28", null),
            candidate("CORRECT-D", "980000000004", "Phạm Văn Dũng", "27", null)
        }));
        assertThat(plan.totalStudents()).isEqualTo(4);
        plan = allocation.previewAllocation(plan.id(), new PreviewAllocationCommand(plan.revision()));
        assertThat(plan.classes()).hasSize(1);
        assertThat(plan.classes().getFirst().reserveUsed()).isEqualTo(1);
        final var preview = plan;
        assertThatThrownBy(() -> confirmation.confirmEnrollment(
                        preview.id(), new ConfirmEnrollmentCommand(preview.revision(), false, false, null)))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);
        var completed = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), true, false, null));
        assertThat(completed.enrolled()).isEqualTo(4);
        for (var row : completed.applicants()) {
            if (unchanged.containsKey(row.applicationCode()))
                assertThat(row.studentCode()).isEqualTo(unchanged.get(row.applicationCode()));
        }
    }

    @Test
    void emailIsOptionalAndNotAnIdentityKeyAndOversizedInvalidRowsRemainReviewable() throws Exception {
        var plan = ready(
                1,
                3,
                0,
                candidate("EMAIL-A", "990000000001", "Nguyễn Văn An", "25", "shared@example.com"),
                candidate("EMAIL-B", "990000000002", "Trần Văn Bình", "24", "shared@example.com"),
                candidate("INVALID-LONG", "9900000000003", "X".repeat(300), "23", null));
        assertThat(plan.totalStudents()).isEqualTo(2);
        assertThat(plan.applicants().getLast().state()).isEqualTo("INVALID");
        var result = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        assertThat(result.enrolled()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    @Test
    void createsClassNamesThroughZAndAAAndUsesSuffixForStudentCode() {
        Long freshCohort = jdbc.queryForObject(
                "insert into cohorts(cohort_code, code_sequence, is_deleted) values ('K9',9,false) returning id",
                Long.class);
        var plan = planning.createPlan(new CreatePlanCommand(majorId, freshCohort, 27, 1, 1, 0));
        plan = prepared(plan, 27);
        assertThat(plan.classes().getFirst().className()).isEqualTo("CT9A");
        assertThat(plan.classes().get(25).className()).isEqualTo("CT9Z");
        assertThat(plan.classes().get(26).className()).isEqualTo("CT9AA");
        assertThat(plan.classes().stream().map(ClassResult::className).toList()).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject(
                        "select count(*) from students s join administrative_classes c "
                                + "on s.class_id=c.id where c.cohort_id=?",
                        Integer.class,
                        freshCohort))
                .isZero();
        var target = new com.eduhub.edu_hub.people.domain.model.AdministrativeClass();
        target.setId(plan.classes().get(26).id());
        target.setClassName(plan.classes().get(26).className());
        var major = com.eduhub.edu_hub.curriculum.domain.model.Major.builder().build();
        major.setId(majorId);
        target.setMajor(major);
        var cohort = new com.eduhub.edu_hub.people.domain.model.Cohort();
        cohort.setCodeSequence(9);
        target.setCohort(cohort);
        assertThat(studentCodes.generate(target, 1)).isEqualTo("CNTT092701");
    }

    @Test
    void allowsSameClassNameAcrossMajorsAndReportsPrefixCollision() {
        Long firstMajor = jdbc.queryForObject(
                "insert into majors(major_code,major_name,status,is_deleted) "
                        + "values ('COLLISIONA','Cơ điện tử','ACTIVE',false) returning id",
                Long.class);
        Long secondMajor = jdbc.queryForObject(
                "insert into majors(major_code,major_name,status,is_deleted) "
                        + "values ('COLLISIONB','Công tác','ACTIVE',false) returning id",
                Long.class);
        var first = planning.createPlan(new CreatePlanCommand(firstMajor, cohortId, 1, 1, 5, 0));
        first = prepared(first, 1);
        var second = planning.createPlan(new CreatePlanCommand(secondMajor, cohortId, 1, 1, 5, 0));
        second = prepared(second, 1);
        assertThat(first.classes().getFirst().className()).isEqualTo("CT8A");
        assertThat(second.classes().getFirst().className()).isEqualTo("CT8A");
        assertThat(first.classes().getFirst().id())
                .isNotEqualTo(second.classes().getFirst().id());
        assertThat(first.warnings())
                .anyMatch(value -> value.contains("Tiền tố lớp CT") && value.contains("COLLISIONB"));
        assertThat(second.warnings())
                .anyMatch(value -> value.contains("Tiền tố lớp CT") && value.contains("COLLISIONA"));
    }

    @Test
    void rejectsWrongMajorWithoutReplacingApplicantsOrTheirPreviewAssignments() throws Exception {
        var plan = ready(1, 3, 0, candidate("SCOPE-KEEP", "911000000001", "Nguyễn Văn An", "25", null));
        Long otherMajor = jdbc.queryForObject("select id from majors where major_code='ATTT'", Long.class);
        var wrongFile = spreadsheet(otherMajor, cohortId, new String[][] {
            candidate("SCOPE-WRONG", "911000000002", "Trần Văn Bình", "24", null)
        });
        assertThatThrownBy(() -> imports.importApplicants(plan.id(), plan.revision(), wrongFile))
                .isInstanceOfSatisfying(AdmissionImportScopeException.class, error -> assertThat(error.getErrors())
                        .singleElement()
                        .satisfies(detail -> assertThat(detail.getMessage()).contains("ATTT", "CNTT")));
        var unchanged = planning.getPlan(plan.id());
        assertThat(unchanged.revision()).isEqualTo(plan.revision());
        assertThat(unchanged.state()).isEqualTo("PREVIEWED");
        assertThat(unchanged.totalStudents()).isEqualTo(plan.totalStudents());
        assertThat(unchanged.majorId()).isEqualTo(majorId);
        assertThat(unchanged.applicants()).isEqualTo(plan.applicants());
        assertThat(unchanged.classes()).isEqualTo(plan.classes());
    }

    @Test
    void rejectsWrongCohortWithoutMergingIntoAnAlreadyConfirmedPlan() throws Exception {
        var ready = ready(1, 3, 0, candidate("SCOPE-DONE", "911000000003", "Nguyễn Văn An", "25", null));
        confirmation.confirmEnrollment(ready.id(), new ConfirmEnrollmentCommand(ready.revision(), false, false, null));
        var plan = planning.getPlan(ready.id());
        long admissionsBefore = count("student_admissions");
        Long otherCohort = jdbc.queryForObject(
                "insert into cohorts(cohort_code,code_sequence,is_deleted) values ('K10',10,false) returning id",
                Long.class);
        var wrongFile = spreadsheet(majorId, otherCohort, new String[][] {
            candidate("SCOPE-WRONG-COHORT", "911000000004", "Trần Văn Bình", "24", null)
        });
        assertThatThrownBy(() -> imports.importApplicants(plan.id(), plan.revision(), wrongFile))
                .isInstanceOfSatisfying(AdmissionImportScopeException.class, error -> assertThat(error.getErrors())
                        .singleElement()
                        .satisfies(detail -> assertThat(detail.getMessage()).contains("K10", "CT8")));
        var unchanged = planning.getPlan(plan.id());
        assertThat(unchanged.revision()).isEqualTo(plan.revision());
        assertThat(unchanged.state()).isEqualTo("CONFIRMED");
        assertThat(unchanged.cohortId()).isEqualTo(cohortId);
        assertThat(unchanged.applicants()).isEqualTo(plan.applicants());
        assertThat(count("student_admissions")).isEqualTo(admissionsBefore);
    }

    @Test
    void rejectsMissingMetadataWithoutDeletingPreviouslyImportedRows() throws Exception {
        var plan = imported(1, 3, 0, candidate("SCOPE-MISSING", "911000000005", "Nguyễn Văn An", "25", null));
        try (var workbook =
                new XSSFWorkbook(new ByteArrayInputStream(planning.generateImportTemplate(majorId, cohortId)))) {
            workbook.getSheetAt(0).getRow(0).getCell(1).setBlank();
            var wrongFile = file(workbook);
            assertThatThrownBy(() -> imports.importApplicants(plan.id(), plan.revision(), wrongFile))
                    .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                            .isEqualTo(ErrorCode.ADMISSION_IMPORT_METADATA_REQUIRED));
        }
        var unchanged = planning.getPlan(plan.id());
        assertThat(unchanged.revision()).isEqualTo(plan.revision());
        assertThat(unchanged.applicants()).isEqualTo(plan.applicants());
        assertThat(unchanged.totalStudents()).isEqualTo(1);
    }

    @Test
    void rollsBackNewPlanWhenTheFileDoesNotMatchTheSelectedMajor() throws Exception {
        long plansBefore = count("admission_plans");
        long applicantsBefore = count("admission_applicants");
        Long otherMajor = jdbc.queryForObject("select id from majors where major_code='ATTT'", Long.class);
        var wrongFile =
                spreadsheet(new String[][] {candidate("SCOPE-ROLLBACK", "911000000006", "Nguyễn Văn An", "25", null)});
        assertThatThrownBy(() ->
                        planning.createPlanFromFile(new CreatePlanCommand(otherMajor, cohortId, 0, 1, 3, 0), wrongFile))
                .isInstanceOf(AdmissionImportScopeException.class);
        assertThat(count("admission_plans")).isEqualTo(plansBefore);
        assertThat(count("admission_applicants")).isEqualTo(applicantsBefore);
    }

    @Test
    void templateRequiresAnExistingMajorAndCohortFromTheCatalog() {
        assertThatThrownBy(() -> planning.generateImportTemplate(Long.MAX_VALUE, cohortId))
                .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STUDENT_CODE_MAJOR));
        assertThatThrownBy(() -> planning.generateImportTemplate(majorId, Long.MAX_VALUE))
                .isInstanceOfSatisfying(BaseException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STUDENT_CODE_COHORT));
    }

    @Test
    void reusesExistingStudentThroughFacadeWithoutResettingAccount() throws Exception {
        var original = ready(1, 3, 0, candidate("BOUNDARY-REUSE-OLD", "912000000003", "Nguyễn Văn An", "25", null));
        var first = confirmation.confirmEnrollment(
                original.id(), new ConfirmEnrollmentCommand(original.revision(), false, false, null));
        var student = first.applicants().getFirst();
        Long userId = jdbc.queryForObject("select user_id from students where id=?", Long.class, student.studentId());
        String password = new BCryptPasswordEncoder().encode("existing-facade-password");
        jdbc.update(
                "update users set password_hash=?,is_first_login=false,status='LOCKED' where id=?", password, userId);
        long studentsBefore = count("students");
        long usersBefore = count("users");
        var next = ready(
                1,
                3,
                0,
                candidate("BOUNDARY-REUSE-NEW", "912000000003", "Nguyễn Văn An", "26", "facade-reuse@example.com"));
        var result = confirmation.confirmEnrollment(
                next.id(), new ConfirmEnrollmentCommand(next.revision(), false, false, null));
        assertThat(result.enrolled()).isEqualTo(1);
        assertThat(result.applicants()).singleElement().satisfies(row -> {
            assertThat(row.studentId()).isEqualTo(student.studentId());
            assertThat(row.studentCode()).isEqualTo(student.studentCode());
            assertThat(row.reusingAccount()).isTrue();
            assertThat(row.warnings()).anyMatch(message -> message.contains("khóa"));
        });
        assertThat(count("students")).isEqualTo(studentsBefore);
        assertThat(count("users")).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("select password_hash from users where id=?", String.class, userId))
                .isEqualTo(password);
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, userId))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject("select is_first_login from users where id=?", Boolean.class, userId))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from student_admissions where student_id=?",
                        Integer.class,
                        student.studentId()))
                .isEqualTo(2);
        sender.sendScheduledAttempt(
                notices.listPlanNotifications(next.id()).getFirst().id());
        verify(mail)
                .sendEmail(
                        eq("facade-reuse@example.com"),
                        argThat(email -> email.subject().contains("Xác nhận nhập học")
                                && email.textBody().contains(student.studentCode())
                                && !email.textBody().contains("12345")));
        verifyNoMoreInteractions(mail);
    }

    @Test
    void rollsBackPeopleAndIdentityChangesWhenSavingAdmissionFailsAndKeepsSuccessfulRows() throws Exception {
        var plan = ready(
                1,
                3,
                0,
                candidate("BOUNDARY-ROLLBACK-A", "912000000001", "Nguyễn Văn An", "25", null),
                candidate("BOUNDARY-ROLLBACK-B", "912000000002", "Trần Văn Bình", "24", null));
        long studentsBefore = count("students");
        long usersBefore = count("users");
        long admissionsBefore = count("student_admissions");
        long noticesBefore = count("admission_notifications");
        Long classId = plan.classes().getFirst().id();
        // Chỉ tạo lỗi trong PostgreSQL Testcontainers, sau khi facade đã lưu Student/User/MSSV.
        jdbc.execute(
                """
                create function admission_workflow.reject_refactor_test_enrollment() returns trigger
                language plpgsql as $$
                begin
                    if new.application_code = 'BOUNDARY-ROLLBACK-B' then
                        raise exception 'Simulated admission persistence failure';
                    end if;
                    return new;
                end;
                $$
                """);
        try {
            jdbc.execute(
                    """
                    create trigger reject_refactor_test_enrollment before insert on student_admissions
                    for each row execute function admission_workflow.reject_refactor_test_enrollment()
                    """);
            var result = confirmation.confirmEnrollment(
                    plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
            assertThat(result.state()).isEqualTo("PARTIALLY_CONFIRMED");
            assertThat(result.enrolled()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(count("students")).isEqualTo(studentsBefore + 1);
            assertThat(count("users")).isEqualTo(usersBefore + 1);
            assertThat(count("student_admissions")).isEqualTo(admissionsBefore + 1);
            assertThat(count("admission_notifications")).isEqualTo(noticesBefore + 1);
            assertThat(jdbc.queryForObject("select count(*) from students where citizen_id='912000000002'", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "select last_student_sequence from administrative_classes where id=?",
                            Integer.class,
                            classId))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger if exists reject_refactor_test_enrollment on student_admissions");
            jdbc.execute("drop function admission_workflow.reject_refactor_test_enrollment()");
        }
        var retry = planning.getPlan(plan.id());
        var completed = confirmation.confirmEnrollment(
                retry.id(), new ConfirmEnrollmentCommand(retry.revision(), false, false, null));
        assertThat(completed.enrolled()).isEqualTo(2);
        assertThat(completed.failed()).isZero();
        assertThat(completed.applicants().getLast().studentCode()).endsWith("02");
        assertThat(count("students")).isEqualTo(studentsBefore + 2);
        assertThat(count("users")).isEqualTo(usersBefore + 2);
        verifyNoInteractions(mail);
    }

    @Test
    void notificationQueueRequiresTheCallerTransaction() {
        assertThatThrownBy(
                        () -> notificationQueue.enqueueAdmissionNotification(QueueAdmissionNotificationCommand.builder()
                                .admissionId(1L)
                                .studentId(1L)
                                .build()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void queuePersistenceFailureRollsBackAdmissionStudentAccountAndCodeCounter() throws Exception {
        var plan = ready(
                1,
                3,
                0,
                candidate(
                        "NOTIFICATION-ROLLBACK", "913000000001", "Nguyễn Văn An", "25", "queue-rollback@example.com"));
        long studentsBefore = count("students");
        long usersBefore = count("users");
        long admissionsBefore = count("student_admissions");
        long notificationsBefore = count("admission_notifications");
        jdbc.execute(
                """
                create function admission_workflow.reject_notification_refactor_test() returns trigger
                language plpgsql as $$
                begin
                    if new.recipient = 'queue-rollback@example.com' then
                        raise exception 'Simulated queue persistence failure';
                    end if;
                    return new;
                end;
                $$
                """);
        try {
            jdbc.execute(
                    """
                    create trigger reject_notification_refactor_test after insert on admission_notifications
                    for each row execute function admission_workflow.reject_notification_refactor_test()
                    """);
            var result = confirmation.confirmEnrollment(
                    plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.enrolled()).isZero();
            assertThat(count("students")).isEqualTo(studentsBefore);
            assertThat(count("users")).isEqualTo(usersBefore);
            assertThat(count("student_admissions")).isEqualTo(admissionsBefore);
            assertThat(count("admission_notifications")).isEqualTo(notificationsBefore);
            assertThat(notices.listPlanNotifications(plan.id())).isEmpty();
            assertThat(jdbc.queryForObject(
                            "select last_student_sequence from administrative_classes where id=?",
                            Integer.class,
                            plan.classes().getFirst().id()))
                    .isZero();
            verifyNoInteractions(mail);
        } finally {
            jdbc.execute("drop trigger if exists reject_notification_refactor_test on admission_notifications");
            jdbc.execute("drop function admission_workflow.reject_notification_refactor_test()");
        }
    }

    @Test
    void renderingFailureDoesNotUndoEnrollmentAndIsRecordedForScheduledRetry() throws Exception {
        var plan = ready(
                1,
                3,
                0,
                candidate("HTML-RENDER-FAIL", "914000000001", "Nguyễn Văn An", "25", "render-failure@example.com"));
        var result = confirmation.confirmEnrollment(
                plan.id(), new ConfirmEnrollmentCommand(plan.revision(), false, false, null));
        assertThat(result.enrolled()).isEqualTo(1);
        doThrow(new IllegalStateException("Template unavailable"))
                .when(templates)
                .renderAccountEmail(any());
        var notice = notices.listPlanNotifications(plan.id()).getFirst();
        sender.sendInitialAttempt(notice.id());
        var failed = notices.listPlanNotifications(plan.id()).getFirst();
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.failureCount()).isEqualTo(1);
        assertThat(failed.error()).isEqualTo("EMAIL_TEMPLATE_RENDER_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from students where citizen_id='914000000001'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from student_admissions where application_code='HTML-RENDER-FAIL'",
                        Integer.class))
                .isEqualTo(1);
        verifyNoInteractions(mail);
    }

    private PlanResult imported(int minimum, int maximum, int reserve, String[]... rows) throws Exception {
        return planning.createPlanFromFile(
                new CreatePlanCommand(majorId, cohortId, 0, minimum, maximum, reserve), spreadsheet(rows));
    }

    private PlanResult prepared(PlanResult plan, int count) {
        return classes.prepareClasses(
                new PrepareClassesCommand(plan.id(), plan.revision(), count, List.of(), false, false, null));
    }

    private PlanResult ready(int minimum, int maximum, int reserve, String[]... rows) throws Exception {
        var plan = imported(minimum, maximum, reserve, rows);
        plan = prepared(plan, plan.scenarios().getFirst().classCount());
        return allocation.previewAllocation(plan.id(), new PreviewAllocationCommand(plan.revision()));
    }

    private String[] candidate(String code, String citizen, String name, String score, String email) {
        return new String[] {code, citizen, name, score, null, null, email, null};
    }

    private MockMultipartFile spreadsheet(String[][] data) throws Exception {
        return spreadsheet(majorId, cohortId, data);
    }

    private MockMultipartFile spreadsheet(Long templateMajor, Long templateCohort, String[][] data) throws Exception {
        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(planning.generateImportTemplate(templateMajor, templateCohort)))) {
            var sheet = workbook.getSheetAt(0);
            for (int i = 0; i < data.length; i++) {
                var row = sheet.createRow(AdmissionSpreadsheetReader.HEADER_ROW_INDEX + i + 1);
                for (int j = 0; j < data[i].length; j++)
                    if (data[i][j] != null) row.createCell(j).setCellValue(data[i][j]);
            }
            return file(workbook);
        }
    }

    private MockMultipartFile file(XSSFWorkbook workbook) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return new MockMultipartFile("file", "admission.xlsx", "application/octet-stream", output.toByteArray());
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
