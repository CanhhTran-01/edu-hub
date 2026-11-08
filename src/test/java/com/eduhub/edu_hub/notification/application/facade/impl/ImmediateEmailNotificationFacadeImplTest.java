package com.eduhub.edu_hub.notification.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAdmissionConfirmationEmailRequest;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.notification.infrastructure.mail.SmtpEmailSender;
import com.eduhub.edu_hub.notification.infrastructure.template.EmailTemplateRenderer;
import com.eduhub.edu_hub.notification.infrastructure.template.RenderedEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class ImmediateEmailNotificationFacadeImplTest {
    private final SmtpEmailSender mail = mock(SmtpEmailSender.class);
    private final EmailTemplateRenderer templates = mock(EmailTemplateRenderer.class);
    private final ImmediateEmailNotificationFacadeImpl facade =
            new ImmediateEmailNotificationFacadeImpl(mail, templates);
    private final RenderedEmail accountEmail = RenderedEmail.builder()
            .subject("Thông tin tài khoản")
            .textBody("Thông tin tài khoản")
            .htmlBody("<p>Thông tin tài khoản</p>")
            .build();
    private final RenderedEmail admissionEmail = RenderedEmail.builder()
            .subject("Xác nhận nhập học")
            .textBody("Dùng mật khẩu hiện tại")
            .htmlBody("<p>Dùng mật khẩu hiện tại</p>")
            .build();

    @BeforeEach
    void renderedContent() {
        when(templates.renderAccountEmail(any())).thenReturn(accountEmail);
        when(templates.renderAdmissionConfirmationEmail(any())).thenReturn(admissionEmail);
    }

    @Test
    void reportsAccountDeliveryWithoutExposingTransportDetails() {
        var result = facade.sendAccountEmail(SendAccountEmailRequest.builder()
                .recipient("student@example.com")
                .username("CNTT080101")
                .temporaryPassword("12345")
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());
        assertThat(result.sent()).isTrue();
        assertThat(result.error()).isNull();
        verify(mail).sendEmail("student@example.com", accountEmail);
    }

    @Test
    void returnsFailureInsteadOfInterruptingAccountCreation() {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).sendEmail("student@example.com", accountEmail);
        var result = facade.sendAccountEmail(SendAccountEmailRequest.builder()
                .recipient("student@example.com")
                .temporaryPassword("temporary")
                .templateType(EmailTemplateType.PASSWORD_RESET)
                .build());
        assertThat(result.sent()).isFalse();
        assertThat(result.error()).isEqualTo("EMAIL_SEND_FAILED");
    }

    @Test
    void reEnrollmentUsesConfirmationWithoutInitialPassword() {
        var result = facade.sendAdmissionConfirmationEmail(SendAdmissionConfirmationEmailRequest.builder()
                .recipient("student@example.com")
                .username("CNTT080101")
                .build());
        assertThat(result.sent()).isTrue();
        verify(mail).sendEmail("student@example.com", admissionEmail);
        verifyNoMoreInteractions(mail);
    }

    @Test
    void renderFailureReturnsASeparateErrorWithoutCallingSmtp() {
        when(templates.renderAccountEmail(any())).thenThrow(new IllegalStateException("Template unavailable"));
        var result = facade.sendAccountEmail(SendAccountEmailRequest.builder()
                .recipient("student@example.com")
                .username("CNTT080101")
                .temporaryPassword("12345")
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());
        assertThat(result.sent()).isFalse();
        assertThat(result.error()).isEqualTo("EMAIL_TEMPLATE_RENDER_FAILED");
        verifyNoInteractions(mail);
    }
}
