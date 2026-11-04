package com.eduhub.edu_hub.people.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.people.api.dto.AdmissionPreviewResponse;
import com.eduhub.edu_hub.people.application.StudentAdmissionService;
import com.eduhub.edu_hub.people.application.StudentApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentControllerAdmissionTest {
    @Test
    void acceptsExcelAsMultipartFormData() throws Exception {
        StudentApplicationService studentService = mock(StudentApplicationService.class);
        StudentAdmissionService admissionService = mock(StudentAdmissionService.class);
        AdmissionPreviewResponse response = new AdmissionPreviewResponse(
                UUID.randomUUID(), "students.xlsx", 1, 1, 0, List.of(), List.of(), List.of(), List.of());
        when(admissionService.preview(any())).thenReturn(response);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StudentController(studentService, admissionService)).build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/v1/students/admission/preview")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());
    }
}
