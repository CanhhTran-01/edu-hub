package com.eduhub.edu_hub.notification.infrastructure.mail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class SmtpEmailSenderTest {
    private final JavaMailSender transport = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(transport);
    private final RenderedEmail email = RenderedEmail.builder()
            .subject("EduHub - Thông tin tài khoản")
            .textBody("Tên đăng nhập: CNTT080101\nMật khẩu khởi tạo: 12345")
            .htmlBody("<html><body><p>Tên đăng nhập: CNTT080101</p><p>Mật khẩu khởi tạo: 12345</p></body></html>")
            .build();

    @BeforeEach
    void configureFrom() {
        ReflectionTestUtils.setField(sender, "senderAddress", "eduhub@example.com");
        when(transport.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendsUtf8MimeWithBothPlainTextAndHtmlAlternatives() throws Exception {
        sender.sendEmail("student@example.com", email);
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(transport).send(captor.capture());
        var message = captor.getValue();
        message.saveChanges();
        assertThat(message.getFrom()[0].toString()).isEqualTo("eduhub@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("student@example.com");
        assertThat(message.getSubject()).isEqualTo(email.subject());
        assertThat(message.isMimeType("multipart/*")).isTrue();
        Map<String, String> bodies = new HashMap<>();
        collectBodies(message, bodies);
        assertThat(bodies).containsEntry("text/plain", email.textBody()).containsEntry("text/html", email.htmlBody());
    }

    @Test
    void missingSenderConfigurationFailsBeforeUsingSmtp() {
        ReflectionTestUtils.setField(sender, "senderAddress", "");
        assertThatThrownBy(() -> sender.sendEmail("student@example.com", email)).isInstanceOf(MailSendException.class);
        verifyNoInteractions(transport);
    }

    @Test
    void invalidRecipientIsReportedAsMailPreparationFailure() {
        assertThatThrownBy(() -> sender.sendEmail("bad@@example.com", email))
                .isInstanceOf(MailPreparationException.class);
        verify(transport, never()).send(any(MimeMessage.class));
    }

    @Test
    void transportFailureRemainsAMailExceptionForTheFacade() {
        doThrow(new MailSendException("SMTP unavailable")).when(transport).send(any(MimeMessage.class));
        assertThatThrownBy(() -> sender.sendEmail("student@example.com", email)).isInstanceOf(MailSendException.class);
    }

    private void collectBodies(Part part, Map<String, String> bodies) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) collectBodies(multipart.getBodyPart(i), bodies);
        } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            assertThat(part.getContentType()).containsIgnoringCase("charset=UTF-8");
            bodies.put(part.isMimeType("text/html") ? "text/html" : "text/plain", (String) part.getContent());
        }
    }
}
