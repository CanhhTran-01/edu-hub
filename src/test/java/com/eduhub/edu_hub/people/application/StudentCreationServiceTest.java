package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.application.CreateStudentAccountResult;
import com.eduhub.edu_hub.identity.application.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.CreateStudentRequest;
import com.eduhub.edu_hub.people.application.admission.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.audit.AuditLogRepository;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StudentCreationServiceTest {
    @Mock StudentRepository students;
    @Mock AdministrativeClassRepository classes;
    @Mock StudentCodeGenerator generator;
    @Mock IdentityFacade identity;
    @Mock AuditLogRepository audit;
    private StudentApplicationService service;
    private AdministrativeClass administrativeClass;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var majors = mock(CurriculumFacade.Majors.class);
        org.mockito.Mockito.lenient().when(majors.requireMajor(any())).thenReturn(
                new com.eduhub.edu_hub.curriculum.api.dto.MajorResponse(1L, "CNTT", "CNTT"));
        service = new StudentApplicationService(students, majors, classes, (CurriculumFacade.ReferenceData) generator, (StudentCodeGenerator) identity, (IdentityFacade) audit,
                (AuditLogRepository) transactionManager, new TransactionTemplate(transactionManager));
        when(identity.requireAcademicAdmin(any())).thenReturn("admin_test");

        Cohort cohort = new Cohort();
        cohort.setId(8L);
        cohort.setCohortCode("CT8");
        administrativeClass = new AdministrativeClass();
        administrativeClass.setId(1L);
        administrativeClass.setClassName("L01");
        administrativeClass.setMajorId(10L);
        administrativeClass.setMajorCode("CT");
        administrativeClass.setMajorName("Công nghệ thông tin");
        administrativeClass.setCohort(cohort);
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        administrativeClass.setMaxCapacity(50);
    }

    @Test
    void createsStudentThenSendsNotificationAndNormalizesIdentityFields() {
        CreateStudentRequest request = request();
        request.setEmail("  Student@Test.COM  ");
        request.setCitizenId("012 345 678 901");
        when(classes.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(administrativeClass));
        when(generator.nextSequence(administrativeClass)).thenReturn(1);
        when(generator.generate(administrativeClass, 1)).thenReturn("CT080101");
        when(identity.createStudentAccountWithoutNotification(any())).thenReturn(20L);
        when(identity.sendStudentAccountNotification(20L, "12345"))
                .thenReturn(new CreateStudentAccountResult(20L, true, null));
        when(students.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            if (student.getId() == null) student.setId(30L);
            return student;
        });
        when(students.findByIdAndDeletedFalse(30L)).thenAnswer(invocation -> Optional.of(savedStudent()));

        var response = service.create(request, " admin_test ");

        assertThat(response.notificationStatus()).isEqualTo("SENT");
        verify(identity).createStudentAccountWithoutNotification(any());
        verify(identity).sendStudentAccountNotification(20L, "12345");
        verify(students).existsByEmailIgnoreCaseAndDeletedFalse("student@test.com");
        verify(students).existsByCitizenIdAndDeletedFalse("012345678901");
    }

    @Test
    void rejectsClassAtMaximumCapacityBeforeCreatingAccount() {
        when(classes.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(administrativeClass));
        when(students.countByAdministrativeClass_IdAndDeletedFalse(1L)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(request(), "admin"))
                .isInstanceOfSatisfying(BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CLASS_CAPACITY_EXCEEDED));
        verify(identity, never()).createStudentAccountWithoutNotification(any());
    }

    @Test
    void emailFailureDoesNotRemoveCreatedStudentAndIsPersistedAsFailed() {
        when(classes.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(administrativeClass));
        when(generator.nextSequence(any())).thenReturn(1);
        when(generator.generate(any(), anyInt())).thenReturn("CT080101");
        when(identity.createStudentAccountWithoutNotification(any())).thenReturn(20L);
        when(identity.sendStudentAccountNotification(20L, "12345"))
                .thenReturn(new CreateStudentAccountResult(20L, false, "EMAIL_SEND_FAILED"));
        when(students.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            if (student.getId() == null) student.setId(30L);
            return student;
        });
        when(students.findByIdAndDeletedFalse(30L)).thenAnswer(invocation -> Optional.of(savedStudent()));

        var response = service.create(request(), "admin");

        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        assertThat(response.notificationError()).isEqualTo("EMAIL_SEND_FAILED");
        verify(students, times(2)).save(any(Student.class));
    }

    @Test
    void resendUsesExistingAccountWithoutCreatingStudentOrUserAgain() {
        Student student = savedStudent();
        student.setAccountNotificationStatus(AccountNotificationStatus.FAILED);
        when(students.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(student));
        when(identity.sendStudentAccountNotification(20L, "12345"))
                .thenReturn(new CreateStudentAccountResult(20L, true, null));
        when(students.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resendAccountNotification(30L, "admin");

        assertThat(response.notificationStatus()).isEqualTo("SENT");
        verify(identity, never()).createStudentAccountWithoutNotification(any());
        verify(identity).sendStudentAccountNotification(20L, "12345");
    }

    private CreateStudentRequest request() {
        CreateStudentRequest request = new CreateStudentRequest();
        request.setFullName("Nguyễn Văn An");
        request.setDateOfBirth(LocalDate.of(2006, 8, 21));
        request.setGender("Nam");
        request.setEmail("student@test.com");
        request.setCitizenId("012345678901");
        request.setMajorId(10L);
        request.setCohortId(8L);
        request.setAdministrativeClassId(1L);
        request.setTrainingType(TrainingType.CHINH_QUY);
        request.setEntranceScore(new BigDecimal("25.50"));
        request.setReason(" Bổ sung hồ sơ ngoài đợt tuyển sinh ");
        return request;
    }

    private Student savedStudent() {
        Student student = new Student();
        student.setId(30L);
        student.setUserId(20L);
        student.setStudentCode("CT080101");
        student.setFullName("Nguyễn Văn An");
        student.setAdministrativeClass(administrativeClass);
        student.setAccountNotificationStatus(AccountNotificationStatus.SENT);
        return student;
    }
}
