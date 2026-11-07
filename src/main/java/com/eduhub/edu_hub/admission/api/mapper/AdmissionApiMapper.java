package com.eduhub.edu_hub.admission.api.mapper;

import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.AdjustAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.ConfirmRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.CreateClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PreviewRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.ConfirmationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.NotificationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.AdjustAssignmentsCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.ConfirmCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.CreateClassesCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PlanCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PreviewCommand;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.AdministrativeClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ConfirmationResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.NotificationResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdmissionApiMapper {
    PlanCommand toCommand(PlanRequest request);

    CreateClassesCommand toCommand(CreateClassesRequest request);

    PreviewCommand toCommand(PreviewRequest request);

    AdjustAssignmentsCommand toCommand(AdjustAssignmentsRequest request);

    ConfirmCommand toCommand(ConfirmRequest request);

    PlanResponse toResponse(PlanResult result);

    ConfirmationResponse toResponse(ConfirmationResult result);

    NotificationResponse toResponse(NotificationResult result);

    AdministrativeClassResponse toResponse(AdministrativeClassResult result);
}
