package com.eduhub.edu_hub.notification.infrastructure.template;

import com.eduhub.edu_hub.notification.application.command.AccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionEmailCommand;
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
    private final TemplateEngine engine;
    private final NotificationMailSettings settings;

    public EmailTemplateRenderer(
            @Qualifier("notificationEmailTemplateEngine") TemplateEngine engine, NotificationMailSettings settings) {
        this.engine = engine;
        this.settings = settings;
    }

    public RenderedEmail renderAccount(AccountEmailCommand command) {

        if (command.templateType() != EmailTemplateType.ACCOUNT_CREATED
                && command.templateType() != EmailTemplateType.PASSWORD_RESET) {
            throw new IllegalArgumentException("Account email requires an explicit account template type");
        }

        if (command.temporaryPassword() == null || command.temporaryPassword().isBlank()) {
            throw new IllegalArgumentException("Temporary password is required");
        }

        if (command.templateType() == EmailTemplateType.ACCOUNT_CREATED && optional(command.username()) == null) {
            throw new IllegalArgumentException("New account email requires a username");
        }

        return render(command.templateType(), command.username(), command.temporaryPassword());
    }

    public RenderedEmail renderAdmission(AdmissionEmailCommand command) {
        return render(EmailTemplateType.ADMISSION_CONFIRMED, command.username(), null);
    }

    private RenderedEmail render(EmailTemplateType type, String username, String temporaryPassword) {

        TemplateSpec template =
                switch (type) {
                    case ACCOUNT_CREATED -> new TemplateSpec("account-created", "Thông tin tài khoản");
                    case PASSWORD_RESET -> new TemplateSpec("password-reset", "Mật khẩu tạm thời");
                    case ADMISSION_CONFIRMED -> new TemplateSpec("admission-confirmed", "Xác nhận nhập học");
                };

        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("brandName", settings.getBrandName());
        context.setVariable("loginUrl", optional(settings.getLoginUrl()));
        context.setVariable("supportEmail", optional(settings.getSupportEmail()));
        context.setVariable("username", optional(username));
        context.setVariable("temporaryPassword", temporaryPassword);

        // TODO(NOTIFICATION_SERVICE): Thống nhất quyền sở hữu template và templateCode/data khi tích hợp dịch vụ ngoài.

        return RenderedEmail.builder()
                .subject(settings.getBrandName() + " - " + template.subject())
                .htmlBody(engine.process("mail/" + template.name() + ".html", context))
                .textBody(engine.process("mail/" + template.name() + ".txt", context))
                .build();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record TemplateSpec(String name, String subject) {}
}
