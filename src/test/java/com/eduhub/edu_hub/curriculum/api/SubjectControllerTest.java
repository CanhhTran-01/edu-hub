package com.eduhub.edu_hub.curriculum.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.curriculum.api.controller.SubjectController;
import com.eduhub.edu_hub.curriculum.api.dto.request.CreateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectSearchRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.UpdateSubjectRequest;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectResponse;
import com.eduhub.edu_hub.curriculum.application.SubjectApplicationService;
import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import com.eduhub.edu_hub.shared.common.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {
    @Mock
    private SubjectApplicationService service;

    private final CreateSubjectRequest request = new CreateSubjectRequest();

    @Test
    void createsSubjectWithoutAuthentication() {
        var controller = new SubjectController(service);
        var subject = SubjectResponse.builder().id(14L).build();
        when(service.createSubject(request)).thenReturn(subject);

        var response = controller.createSubject(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(subject);
        assertThat(response.getBody().getMessage()).isEqualTo("Thêm học phần thành công");
    }

    @Test
    void returnsNoResultsMessageWithEmptyPage() {
        var controller = new SubjectController(service);
        var search = new SubjectSearchRequest();
        var page = PageResponse.<SubjectResponse>builder()
                .content(List.of())
                .pageNumber(0)
                .pageSize(20)
                .totalElements(0)
                .totalPages(0)
                .isLast(true)
                .build();
        when(service.searchSubjects(search)).thenReturn(page);

        var response = controller.searchSubjects(search);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Không tìm thấy kết quả");
        assertThat(response.getData()).isSameAs(page);
    }

    @Test
    void returnsSubjectDetail() {
        var controller = new SubjectController(service);
        var subject = SubjectResponse.builder().id(14L).build();
        when(service.getSubjectById(14L)).thenReturn(subject);

        var response = controller.getSubjectById(14L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(subject);
    }

    @Test
    void updatesSubjectAndReturnsConfirmation() {
        var controller = new SubjectController(service);
        var update = new UpdateSubjectRequest();
        var subject = SubjectResponse.builder().id(14L).build();
        when(service.updateSubject(14L, update)).thenReturn(subject);

        var response = controller.updateSubject(14L, update);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Cập nhật học phần thành công");
        assertThat(response.getData()).isSameAs(subject);
    }

    @Test
    void deactivatesSubjectAndReturnsConfirmation() {
        var controller = new SubjectController(service);
        var action = new SubjectActionRequest();
        var subject =
                SubjectResponse.builder().id(14L).status(SubjectStatus.INACTIVE).build();
        when(service.deactivateSubject(14L, action)).thenReturn(subject);

        var response = controller.deactivateSubject(14L, action);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Ngừng sử dụng học phần thành công");
        assertThat(response.getData()).isSameAs(subject);
    }

    @Test
    void deletesSubjectAndReturnsConfirmation() {
        var controller = new SubjectController(service);
        var action = new SubjectActionRequest();

        var response = controller.deleteSubject(14L, action);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Xóa học phần thành công");
        verify(service).deleteSubject(14L, action);
    }

    @Test
    void savesPrerequisitesAndNotifiesAboutDuplicatesAndNonRetroactiveChanges() {
        var controller = new SubjectController(service);
        var request = new SetPrerequisiteRequest();
        request.setPrerequisiteSubjectIds(List.of(2L, 2L));
        var subject = SubjectResponse.builder().id(1L).build();
        when(service.setPrerequisites(1L, request)).thenReturn(subject);

        var response = controller.setPrerequisites(1L, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(subject);
        assertThat(response.getMessage()).contains("trùng", "không hồi tố");
    }
}
