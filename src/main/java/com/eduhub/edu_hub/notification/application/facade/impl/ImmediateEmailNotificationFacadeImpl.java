package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAdmissionConfirmationEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.response.EmailDeliveryResponse;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.infrastructure.mail.SmtpEmailSender;
import com.eduhub.edu_hub.notification.infrastructure.template.EmailTemplateRenderer;
import com.eduhub.edu_hub.notification.infrastructure.template.RenderedEmail;
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
    public EmailDeliveryResponse sendAccountEmail(SendAccountEmailRequest request) {
        return renderAndSendEmail(request.recipient(), () -> templateRenderer.renderAccountEmail(request));
    }

    @Override
    public EmailDeliveryResponse sendAdmissionConfirmationEmail(SendAdmissionConfirmationEmailRequest request) {
        return renderAndSendEmail(
                request.recipient(), () -> templateRenderer.renderAdmissionConfirmationEmail(request));
    }

    private EmailDeliveryResponse renderAndSendEmail(String recipientEmail, Supplier<RenderedEmail> emailRenderer) {

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
            return EmailDeliveryResponse.builder()
                    .sent(false)
                    .error("EMAIL_TEMPLATE_RENDER_FAILED")
                    .build();
        }

        // tiến hành gửi
        return sendRenderedEmail(recipientEmail, renderedEmail);
    }

    private EmailDeliveryResponse sendRenderedEmail(String recipientEmail, RenderedEmail renderedEmail) {
        try {
            smtpEmailSender.sendEmail(recipientEmail, renderedEmail);
            return EmailDeliveryResponse.builder().sent(true).build();
        } catch (MailException exception) {
            // Chỉ log loại lỗi; không ghi recipient, nội dung mail hoặc mật khẩu.
            log.warn(
                    "Notification email delivery failed: failureType={}",
                    exception.getClass().getSimpleName());
            return EmailDeliveryResponse.builder()
                    .sent(false)
                    .error("EMAIL_SEND_FAILED")
                    .build();
        }
    }
}
