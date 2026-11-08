package com.eduhub.edu_hub.notification.api.dto.response;

import lombok.Builder;

@Builder
public record EmailDeliveryResponse(boolean sent, String error) {}
