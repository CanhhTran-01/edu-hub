package com.eduhub.edu_hub.scheduling.api.dto.response;

import java.util.List;

public record ModuleClassPublicationResponse(
        Long semesterId, int changedClassCount, List<ModuleClassResponse> moduleClasses) {}
