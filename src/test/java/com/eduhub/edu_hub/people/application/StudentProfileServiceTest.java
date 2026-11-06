package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateMyStudentProfileRequest;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentProfileServiceTest {
    IdentityFacade identity = mock(IdentityFacade.class);
    StudentRepository students = mock(StudentRepository.class);
    StudentApplicationService queries = mock(StudentApplicationService.class);
    StudentProfileService service = new StudentProfileService(identity, students, queries);
    Student student;

    @BeforeEach
    void setup() {
        when(identity.requireCurrentUser("token"))
                .thenReturn(UserInfoResponse.builder()
                        .id(7L)
                        .username("CT080101")
                        .role("STUDENT")
                        .build());
        student = new Student();
        student.setId(9L);
        student.setUserId(7L);
        student.setStudentCode("CT080101");
        student.setFullName("Original Name");
        student.setPhone("0900000000");
        student.setTargetGpa(new BigDecimal("3.20"));
    }

    @Test
    void getUsesIdentityUserIdNotStudentId() {
        when(students.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(student));
        service.get("token");
        verify(queries).findById(9L);
    }

    @Test
    void patchChangesOnlyProvidedFields() {
        when(students.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyStudentProfileRequest();
        request.setTargetGpa(new BigDecimal("4"));
        service.update("token", request);
        assertThat(student.getTargetGpa()).isEqualByComparingTo("4");
        assertThat(student.getPhone()).isEqualTo("0900000000");
        assertThat(student.getFullName()).isEqualTo("Original Name");
        verify(identity).requireCurrentUser("token");
        verify(students).save(student);
    }

    @Test
    void noOpDoesNotWrite() {
        when(students.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyStudentProfileRequest();
        request.setTargetGpa(new BigDecimal("3.2"));
        service.update("token", request);
        verify(students, never()).save(any());
    }

    @Test
    void explicitNullClearsOnlySpecifiedField() {
        when(students.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyStudentProfileRequest();
        request.setPhone(null);
        service.update("token", request);
        assertThat(student.getPhone()).isNull();
        assertThat(student.getTargetGpa()).isEqualByComparingTo("3.20");
    }

    @Test
    void missingStudentIsRejected() {
        when(students.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("token")).isInstanceOf(BaseException.class);
        verifyNoInteractions(queries);
    }
}
