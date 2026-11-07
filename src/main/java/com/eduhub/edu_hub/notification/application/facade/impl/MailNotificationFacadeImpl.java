package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.application.command.AccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
import com.eduhub.edu_hub.notification.infrastructure.mail.SmtpMailSender;
import com.eduhub.edu_hub.notification.infrastructure.template.EmailTemplateRenderer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.SUPPORTS)
public class MailNotificationFacadeImpl implements NotificationFacade.Mail {

    // TODO(NOTIFICATION_SERVICE): Chốt hợp đồng notification-service rồi thay adapter SMTP tại đây
    //  Chưa thêm HTTP/message broker, multichannel hoặc thay đổi chính sách retry.

    private final SmtpMailSender mail;
    private final EmailTemplateRenderer templates;

    @Override
    public EmailDeliveryResult sendAccount(AccountEmailCommand command) {
        return deliver(command.recipient(), () -> templates.renderAccount(command));
    }

    @Override
    public EmailDeliveryResult sendAdmissionConfirmation(AdmissionEmailCommand command) {
        return deliver(command.recipient(), () -> templates.renderAdmission(command));
    }

    private EmailDeliveryResult deliver(String recipient, Supplier<RenderedEmail> render) {

        RenderedEmail email;

        try {
            email = render.get();

        } catch (RuntimeException exception) {

            // Lỗi template cũng trả kết quả thất bại để không làm hỏng luồng tạo hồ sơ đã commit.

            // Không log exception/context vì có thể chứa dữ liệu hoặc mật khẩu đang render.
            log.warn("Notification email rendering failed: failureType={}", exception.getClass().getSimpleName());

            return EmailDeliveryResult.builder()
                    .sent(false)
                    .error("EMAIL_TEMPLATE_RENDER_FAILED")
                    .build();
        }

        try {
            mail.send(recipient, email);
            return EmailDeliveryResult.builder()
                    .sent(true)
                    .build();

        } catch (MailException exception) {

            // Chỉ log loại lỗi; không ghi recipient, nội dung mail hoặc mật khẩu.
            log.warn("Notification email delivery failed: failureType={}", exception.getClass().getSimpleName());

            return EmailDeliveryResult.builder()
                    .sent(false)
                    .error("EMAIL_SEND_FAILED")
                    .build();
        }
    }
}
