package com.eduhub.edu_hub.notification.infrastructure.template;

import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAdmissionConfirmationEmailRequest;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.notification.infrastructure.config.NotificationMailSettings;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class EmailTemplateRenderer {
    private final TemplateEngine templateEngine;
    private final NotificationMailSettings mailSettings;

    public EmailTemplateRenderer(
            @Qualifier("notificationEmailTemplateEngine") TemplateEngine templateEngine,
            NotificationMailSettings mailSettings) {
        this.templateEngine = templateEngine;
        this.mailSettings = mailSettings;
    }

    public RenderedEmail renderAccountEmail(SendAccountEmailRequest request) {
        validateAccountEmailRequest(request);
        return renderEmailTemplate(request.templateType(), request.username(), request.temporaryPassword());
    }

    public RenderedEmail renderAdmissionConfirmationEmail(SendAdmissionConfirmationEmailRequest request) {
        return renderEmailTemplate(EmailTemplateType.ADMISSION_CONFIRMED, request.username(), null);
    }

    private void validateAccountEmailRequest(SendAccountEmailRequest request) {
        if (request.templateType() != EmailTemplateType.ACCOUNT_CREATED
                && request.templateType() != EmailTemplateType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Account email requires an explicit account template type");
        }

        if (request.temporaryPassword() == null || request.temporaryPassword().isBlank()) {
            throw new IllegalArgumentException("Temporary password is required");
        }

        if (request.templateType() == EmailTemplateType.ACCOUNT_CREATED && trimToNull(request.username()) == null) {
            throw new IllegalArgumentException("New account email requires a username");
        }
    }

    private RenderedEmail renderEmailTemplate(
            EmailTemplateType templateType, String username, String temporaryPassword) {
        EmailTemplateDefinition templateDefinition = resolveTemplateDefinition(templateType);
        Context templateContext = createTemplateContext(username, temporaryPassword);

        // TODO(NOTIFICATION_SERVICE): Thống nhất quyền sở hữu template và templateCode/data khi tích hợp dịch vụ ngoài.

        return RenderedEmail.builder()
                .subject(mailSettings.getBrandName() + " - " + templateDefinition.subjectTitle())
                .htmlBody(
                        templateEngine.process("mail/" + templateDefinition.templateName() + ".html", templateContext))
                .textBody(templateEngine.process("mail/" + templateDefinition.templateName() + ".txt", templateContext))
                .build();
    }

    private EmailTemplateDefinition resolveTemplateDefinition(EmailTemplateType templateType) {
        return switch (templateType) {
            case ACCOUNT_CREATED -> new EmailTemplateDefinition("account-created", "Thông tin tài khoản");
            case PASSWORD_RESET -> new EmailTemplateDefinition("password-reset", "Mật khẩu tạm thời");
            case ADMISSION_CONFIRMED -> new EmailTemplateDefinition("admission-confirmed", "Xác nhận nhập học");
        };
    }

    private Context createTemplateContext(String username, String temporaryPassword) {
        Context templateContext = new Context(Locale.forLanguageTag("vi-VN"));
        templateContext.setVariable("brandName", mailSettings.getBrandName());
        templateContext.setVariable("loginUrl", trimToNull(mailSettings.getLoginUrl()));
        templateContext.setVariable("supportEmail", trimToNull(mailSettings.getSupportEmail()));
        templateContext.setVariable("username", trimToNull(username));
        templateContext.setVariable("temporaryPassword", temporaryPassword);
        return templateContext;
    }

    private String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private record EmailTemplateDefinition(String templateName, String subjectTitle) {}
}
