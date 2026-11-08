package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import com.eduhub.edu_hub.notification.application.result.RenderedEmail;
import com.eduhub.edu_hub.notification.infrastructure.mail.SmtpEmailSender;
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
public class ImmediateEmailNotificationFacadeImpl implements NotificationFacade.ImmediateEmail {

    // TODO(NOTIFICATION_SERVICE): Chốt hợp đồng notification-service rồi thay adapter SMTP tại đây
    //  Chưa thêm HTTP/message broker, multichannel hoặc thay đổi chính sách retry.

    private final SmtpEmailSender smtpEmailSender;
    private final EmailTemplateRenderer templateRenderer;

    @Override
    public EmailDeliveryResult sendAccountEmail(SendAccountEmailCommand command) {
        return renderAndSendEmail(command.recipient(), () -> templateRenderer.renderAccountEmail(command));
    }

    @Override
    public EmailDeliveryResult sendAdmissionConfirmationEmail(SendAdmissionConfirmationEmailCommand command) {
        return renderAndSendEmail(
                command.recipient(), () -> templateRenderer.renderAdmissionConfirmationEmail(command));
    }

    private EmailDeliveryResult renderAndSendEmail(String recipientEmail, Supplier<RenderedEmail> emailRenderer) {

        // Supplier trì hoãn việc render: lambda chỉ chạy khi gọi get() bên dưới,
        // nên lỗi template sẽ bị bắt bởi try/catch này thay vì nổ ở phía gọi.

        RenderedEmail renderedEmail;

        // tiến hành render bằng .get() (thực sự chạy () -> templates.renderAdmission(command) ở đây)
        try {
            renderedEmail = emailRenderer.get();
        } catch (RuntimeException exception) {
            // Lỗi template cũng trả kết quả thất bại để không làm hỏng luồng tạo hồ sơ đã commit.
            // Không log exception/context vì có thể chứa dữ liệu hoặc mật khẩu đang render.
            log.warn(
                    "Notification email rendering failed: failureType={}",
                    exception.getClass().getSimpleName());
            return EmailDeliveryResult.builder()
                    .sent(false)
                    .error("EMAIL_TEMPLATE_RENDER_FAILED")
                    .build();
        }

        // tiến hành gửi
        return sendRenderedEmail(recipientEmail, renderedEmail);
    }

    private EmailDeliveryResult sendRenderedEmail(String recipientEmail, RenderedEmail renderedEmail) {
        try {
            smtpEmailSender.sendEmail(recipientEmail, renderedEmail);
            return EmailDeliveryResult.builder().sent(true).build();
        } catch (MailException exception) {
            // Chỉ log loại lỗi; không ghi recipient, nội dung mail hoặc mật khẩu.
            log.warn(
                    "Notification email delivery failed: failureType={}",
                    exception.getClass().getSimpleName());
            return EmailDeliveryResult.builder()
                    .sent(false)
                    .error("EMAIL_SEND_FAILED")
                    .build();
        }
    }
}
