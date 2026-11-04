package com.eduhub.edu_hub.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
        if (from.isBlank()) {
            throw new org.springframework.mail.MailSendException("Gmail sender is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Smart Edu - Mat khau tam thoi");
        message.setText(
                "Mat khau tam thoi cua ban: " + temporaryPassword + "\nVui long dang nhap va doi mat khau ngay.");
        sender.send(message);
    }
}
