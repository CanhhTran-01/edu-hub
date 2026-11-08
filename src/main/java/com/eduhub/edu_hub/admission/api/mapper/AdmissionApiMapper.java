package com.eduhub.edu_hub.admission.api.mapper;

import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.AdjustAssignmentsRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.ConfirmEnrollmentRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.CreatePlanRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PrepareClassesRequest;
import com.eduhub.edu_hub.admission.api.dto.request.AdmissionRequests.PreviewAllocationRequest;
import com.eduhub.edu_hub.admission.api.dto.response.AdministrativeClassResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.ConfirmationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.NotificationResponse;
import com.eduhub.edu_hub.admission.api.dto.response.AdmissionResponses.PlanResponse;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.AdjustAssignmentsCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.ConfirmEnrollmentCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.CreatePlanCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PrepareClassesCommand;
import com.eduhub.edu_hub.admission.application.command.AdmissionCommands.PreviewAllocationCommand;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.AdministrativeClassResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.ConfirmationResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.NotificationResult;
import com.eduhub.edu_hub.admission.application.result.AdmissionResults.PlanResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdmissionApiMapper {
    CreatePlanCommand toCommand(CreatePlanRequest request);

    PrepareClassesCommand toCommand(PrepareClassesRequest request);

    PreviewAllocationCommand toCommand(PreviewAllocationRequest request);

    AdjustAssignmentsCommand toCommand(AdjustAssignmentsRequest request);

    ConfirmEnrollmentCommand toCommand(ConfirmEnrollmentRequest request);

    PlanResponse toResponse(PlanResult result);

    ConfirmationResponse toResponse(ConfirmationResult result);

    NotificationResponse toResponse(NotificationResult result);

    AdministrativeClassResponse toResponse(AdministrativeClassResult result);
}
