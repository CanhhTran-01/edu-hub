package com.eduhub.edu_hub.notification.infrastructure.mail;

import com.eduhub.edu_hub.notification.infrastructure.template.RenderedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
public class SmtpEmailSender {
    private final JavaMailSender javaMailSender;

    @Value("${GMAIL_ADDRESS:}")
    private String senderAddress;

    public void sendEmail(String recipientEmail, RenderedEmail renderedEmail) {

        if (senderAddress == null || senderAddress.isBlank())
            throw new MailSendException("Gmail sender is not configured");

        MimeMessage mimeMessage = prepareMimeMessage(recipientEmail, renderedEmail);
        javaMailSender.send(mimeMessage);
    }

    private MimeMessage prepareMimeMessage(String recipientEmail, RenderedEmail renderedEmail) {
        var mimeMessage = javaMailSender.createMimeMessage();
        try {
            var messageHelper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            messageHelper.setFrom(senderAddress);
            messageHelper.setTo(recipientEmail);
            messageHelper.setSubject(renderedEmail.subject());
            messageHelper.setText(renderedEmail.textBody(), renderedEmail.htmlBody());
        } catch (MessagingException exception) {
            // Không đưa địa chỉ/nội dung thư vào message lỗi hoặc log.
            throw new MailPreparationException("Cannot prepare notification email", exception);
        }

        return mimeMessage;
    }
}
