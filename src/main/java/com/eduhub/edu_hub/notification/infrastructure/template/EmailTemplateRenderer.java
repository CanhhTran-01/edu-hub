package com.eduhub.edu_hub.notification.infrastructure.template;

import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
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

    public RenderedEmail renderAccountEmail(SendAccountEmailCommand command) {
        validateAccountEmailCommand(command);
        return renderEmailTemplate(command.templateType(), command.username(), command.temporaryPassword());
    }

    public RenderedEmail renderAdmissionConfirmationEmail(SendAdmissionConfirmationEmailCommand command) {
        return renderEmailTemplate(EmailTemplateType.ADMISSION_CONFIRMED, command.username(), null);
    }

    private void validateAccountEmailCommand(SendAccountEmailCommand command) {
        if (command.templateType() != EmailTemplateType.ACCOUNT_CREATED
                && command.templateType() != EmailTemplateType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Account email requires an explicit account template type");
        }

        if (command.temporaryPassword() == null || command.temporaryPassword().isBlank()) {
            throw new IllegalArgumentException("Temporary password is required");
        }

        if (command.templateType() == EmailTemplateType.ACCOUNT_CREATED && trimToNull(command.username()) == null) {
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

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record EmailTemplateDefinition(String templateName, String subjectTitle) {}
}
