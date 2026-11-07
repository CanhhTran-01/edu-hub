package com.eduhub.edu_hub.identity.infrastructure.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordMailService {
    private final JavaMailSender sender;

    @Value("${GMAIL_ADDRESS:}")
    private String from;

    public void send(String recipient, String temporaryPassword) {
        sendAccount(recipient, null, temporaryPassword);
    }

    public void sendAdmissionConfirmation(String recipient, String username) {
        if (from.isBlank()) throw new MailSendException("Gmail sender is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("EduHub - Xác nhận nhập học");
        message.setText("Hồ sơ nhập học của bạn đã được tiếp nhận. Tài khoản hiện có: " + username
                + ". Vui lòng sử dụng mật khẩu hiện tại; mật khẩu không bị đặt lại.");
        sender.send(message);
    }

    public void sendAccount(String recipient, String username, String temporaryPassword) {

        if (from.isBlank()) {
            throw new MailSendException("Gmail sender is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("EduHub - Mật khẩu tạm thời");
        String body =
                """
            Mật khẩu tạm thời của bạn: %s
            Tên đăng nhập: %s
            Vui lòng đăng nhập và đổi mật khẩu ngay.
        """
                        .formatted(temporaryPassword, username == null ? "" : username);
        message.setText(body);

        sender.send(message);
    }
}
