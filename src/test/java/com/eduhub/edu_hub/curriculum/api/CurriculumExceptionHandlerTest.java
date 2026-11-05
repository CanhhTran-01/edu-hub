package com.eduhub.edu_hub.curriculum.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.curriculum.api.controller.SubjectController;
import com.eduhub.edu_hub.curriculum.api.dto.request.SetPrerequisiteRequest;
import com.eduhub.edu_hub.curriculum.api.dto.request.SubjectActionRequest;
import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.api.exception.CurriculumExceptionHandler;
import com.eduhub.edu_hub.curriculum.application.exception.PrerequisiteCycleException;
import com.eduhub.edu_hub.curriculum.application.exception.SubjectHasDependentsException;
import com.eduhub.edu_hub.curriculum.application.SubjectApplicationService;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CurriculumExceptionHandlerTest {
    private final SubjectApplicationService service = mock(SubjectApplicationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SubjectController(service))
            .setControllerAdvice(new GlobalExceptionHandler(), new CurriculumExceptionHandler())
            .build();

    @Test
    void blockedActionListsDependentsAndGuidesPrerequisiteRemoval() throws Exception {
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

    @Test
    void ordinaryBusinessExceptionFallsThroughToGlobalHandler() throws Exception {
        when(service.getSubjectById(99L)).thenThrow(new BaseException(ErrorCode.SUBJECT_NOT_FOUND));

        mvc.perform(get("/api/v1/subjects/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SUBJECT_NOT_FOUND.getCode()));
    }

    @Test
    void invalidIdUsesTheSharedTypeMismatchHandler() throws Exception {
        mvc.perform(get("/api/v1/subjects/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("Giá trị tham số không hợp lệ"));
    }
}
