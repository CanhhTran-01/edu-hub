package com.eduhub.edu_hub.notification.infrastructure.mail;

import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmtpMailSender {
    private final JavaMailSender sender;

    @Value("${GMAIL_ADDRESS:}")
    private String from;

    public void send(String recipient, RenderedEmail email) {

        if (from == null || from.isBlank()) {
            throw new MailSendException("Gmail sender is not configured");
        }

        var message = sender.createMimeMessage();

        try {

            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), email.htmlBody());

        } catch (MessagingException exception) {

            // Không đưa địa chỉ/nội dung thư vào message lỗi hoặc log.
            throw new MailPreparationException("Cannot prepare notification email", exception);
        }

        sender.send(message);
    }
}
