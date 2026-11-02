package com.eduhub.edu_hub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.curriculum.api.controller.SubjectController;
import com.eduhub.edu_hub.curriculum.api.dto.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.SubjectApplicationService;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/subjects");

    @Test
    void malformedJsonReturnsBadRequestInsteadOfServerError() {
        var exception = new HttpMessageNotReadableException("Invalid JSON", new MockHttpInputMessage(new byte[0]));

        var response = handler.handlingHttpMessageNotReadableException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/subjects");
    }

    @Test
    void blockedActionListsDependentsAndGuidesPrerequisiteRemoval() throws Exception {
        var service = mock(SubjectApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SubjectController(service))
                .setControllerAdvice(handler)
                .build();
        var dependent = SubjectSimpleResponse.builder()
                .id(20L)
                .subjectCode("CS201")
                .subjectName("Cấu trúc dữ liệu")
                .build();
        var exception = new SubjectHasDependentsException(List.of(dependent));
        doThrow(exception).when(service).deleteSubject(eq(14L), any(SubjectActionRequest.class));

        mvc.perform(
                        delete("/api/v1/subjects/14")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"reason":"Không còn sử dụng", "confirmed":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUBJECT_IN_USE.getCode()))
                .andExpect(jsonPath("$.message").value(containsString("UC-21")))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("requiredBy"))
                .andExpect(jsonPath("$.errors[0].message").value("CS201 - Cấu trúc dữ liệu"))
                .andExpect(jsonPath("$.path").value("/api/v1/subjects/14"));
    }

    @Test
    void cycleErrorIncludesTheFullCyclePath() throws Exception {
        var service = mock(SubjectApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SubjectController(service))
                .setControllerAdvice(handler)
                .build();
        var a = SubjectSimpleResponse.builder().id(1L).subjectCode("A").build();
        var b = SubjectSimpleResponse.builder().id(2L).subjectCode("B").build();
        var c = SubjectSimpleResponse.builder().id(3L).subjectCode("C").build();
        var exception = new PrerequisiteCycleException(List.of(a, b, c, a));
        when(service.setPrerequisites(eq(1L), any(SetPrerequisiteRequest.class)))
                .thenThrow(exception);

        mvc.perform(
                        post("/api/v1/subjects/1/prerequisites")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"prerequisiteSubjectIds":[2]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.PREREQUISITE_CYCLE.getCode()))
                .andExpect(jsonPath("$.message").value(containsString("A → B → C → A")))
                .andExpect(jsonPath("$.path").value("/api/v1/subjects/1/prerequisites"));
    }
}
