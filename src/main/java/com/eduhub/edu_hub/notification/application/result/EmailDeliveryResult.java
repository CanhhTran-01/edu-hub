package com.eduhub.edu_hub.notification.application.result;

import lombok.Builder;

@Builder
public record EmailDeliveryResult(boolean sent, String error) {}
