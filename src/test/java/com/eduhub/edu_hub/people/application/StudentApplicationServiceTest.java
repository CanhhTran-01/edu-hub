package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.application.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.UpdateStudentRequest;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.audit.AuditLog;
import com.eduhub.edu_hub.shared.audit.AuditLogRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentApplicationServiceTest {
    @Mock
    private StudentRepository students;

    @Mock
    private AdministrativeClassRepository classes;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @Mock
    private IdentityFacade identity;

    @Mock
    private AuditLogRepository auditLogs;

    @InjectMocks
    private StudentApplicationService service;

    @Test
    void warningReasonIsStoredAndClearedTogetherWithWarningStatus() {
        var student = new Student();
        student.setId(1L);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(students.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new UpdateStudentRequest();
        request.setAcademicWarningStatus(" LEVEL_1 ");
        request.setAcademicWarningReason(" GPA dưới ngưỡng ");

        var response = service.update(1L, request);
        assertThat(response.getAcademicWarningStatus()).isEqualTo("LEVEL_1");
        assertThat(response.getAcademicWarningReason()).isEqualTo("GPA dưới ngưỡng");

        request = new UpdateStudentRequest();
        request.setAcademicWarningStatus(" ");
        response = service.update(1L, request);
        assertThat(response.getAcademicWarningStatus()).isNull();
        assertThat(response.getAcademicWarningReason()).isNull();
    }

    @Test
    void profileGetsTheCurrentMajorNameThroughTheCurriculumFacade() {
        var student = new Student();
        var administrativeClass = new AdministrativeClass();
        var major = Major.builder().build();
        major.setId(2L);
        administrativeClass.setMajor(major);
        administrativeClass.setCohort(new Cohort());
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        student.setAdministrativeClass(administrativeClass);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(curriculum.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "ATTT", "Tên ngành từ Curriculum", null)));

        assertThat(service.findById(1L).getMajorName()).isEqualTo("Tên ngành từ Curriculum");
    }

    @Test
    void deactivatesStudentDisablesAccountAndAuditsReason() {
        Student student = student(ActivityStatus.STUDYING);
        when(identity.requireAcademicAdmin("token")).thenReturn("hocvu");
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(students.save(student)).thenReturn(student);

        var response = service.deactivate(1L, " Bảo lưu ", "token");

        assertThat(response.getActivityStatus()).isEqualTo(ActivityStatus.DEACTIVATED);
        verify(identity).disableUser(7L);
        verify(auditLogs).save(argThat(log ->
                log.getAction().equals("DEACTIVATE_STUDENT")
                        && log.getActor().equals("hocvu")
                        && log.getDetails().contains("reason=Bảo lưu")));
    }

    @Test
    void activatesStudentAndEnablesLinkedAccount() {
        Student student = student(ActivityStatus.DEACTIVATED);
        when(identity.requireAcademicAdmin("token")).thenReturn("hocvu");
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(students.save(student)).thenReturn(student);

        var response = service.activate(1L, "Quay lại học", "token");

        assertThat(response.getActivityStatus()).isEqualTo(ActivityStatus.STUDYING);
        verify(identity).enableUser(7L);
        verify(auditLogs).save(any(AuditLog.class));
    }

    @Test
    void rejectsDeletedStudentAndBlankReason() {
        when(identity.requireAcademicAdmin("token")).thenReturn("hocvu");
        assertThatThrownBy(() -> service.deactivate(1L, " ", "token"))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);

        Student deleted = student(ActivityStatus.DELETED);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.activate(1L, "Khôi phục", "token"))
                .isInstanceOf(com.eduhub.edu_hub.shared.exception.BaseException.class);
        verify(identity, never()).enableUser(anyLong());
    }

    private Student student(ActivityStatus status) {
        Student student = new Student();
        student.setId(1L);
        student.setUserId(7L);
        student.setStudentCode("CT080101");
        student.setActivityStatus(status);
        return student;
    }
}
