package com.eduhub.edu_hub.notification.infrastructure.template;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.notification.infrastructure.config.EmailTemplateConfiguration;
import com.eduhub.edu_hub.notification.infrastructure.config.NotificationMailSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {
    private NotificationMailSettings settings;
    private EmailTemplateRenderer renderer;

    @BeforeEach
    void templates() {
        settings = new NotificationMailSettings();
        settings.setLoginUrl("https://eduhub.example.com/login");
        settings.setSupportEmail("support@example.com");
        renderer =
                new EmailTemplateRenderer(new EmailTemplateConfiguration().notificationEmailTemplateEngine(), settings);
    }

    @Test
    void rendersNewAccountWithCredentialsAndResolvedSharedLayout() {
        var email = renderer.renderAccountEmail(SendAccountEmailCommand.builder()
                .recipient("student@example.com")
                .username("CNTT080101")
                .temporaryPassword("12345")
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());
        assertThat(email.subject()).isEqualTo("EduHub - Thông tin tài khoản");
        assertThat(email.htmlBody())
                .contains(
                        "TÀI KHOẢN MỚI",
                        "CNTT080101",
                        "12345",
                        "Đăng nhập hệ thống",
                        "https://eduhub.example.com/login",
                        "mailto:support@example.com")
                .doesNotContain("th:text=", "th:replace=", "${");
        assertThat(email.textBody())
                .contains(
                        "Tên đăng nhập: CNTT080101",
                        "Mật khẩu khởi tạo: 12345",
                        "đổi mật khẩu ngay",
                        "https://eduhub.example.com/login")
                .doesNotContain("<html", "[(${", "[#");
    }

    @Test
    void resetPurposeIsExplicitEvenWhenUsernameIsProvided() {
        var email = renderer.renderAccountEmail(SendAccountEmailCommand.builder()
                .username("CNTT080101")
                .temporaryPassword("new-temporary-password")
                .templateType(EmailTemplateType.PASSWORD_RESET)
                .build());
        assertThat(email.subject()).isEqualTo("EduHub - Mật khẩu tạm thời");
        assertThat(email.htmlBody())
                .contains("BẢO MẬT TÀI KHOẢN", "CNTT080101", "new-temporary-password", "Mật khẩu cũ không còn hiệu lực")
                .doesNotContain("TÀI KHOẢN MỚI");
        assertThat(email.textBody()).contains("Mật khẩu tạm thời: new-temporary-password", "CNTT080101");
    }

    @Test
    void resetWorksWithoutUsernameAndNeverDisplaysNull() {
        var email = renderer.renderAccountEmail(SendAccountEmailCommand.builder()
                .temporaryPassword("temporary-password")
                .templateType(EmailTemplateType.PASSWORD_RESET)
                .build());
        assertThat(email.htmlBody()).contains("temporary-password").doesNotContain("Tên đăng nhập", ">null<");
        assertThat(email.textBody()).contains("temporary-password").doesNotContain("Tên đăng nhập", "null");
    }

    @Test
    void admissionConfirmationUsesExistingAccountWithoutCredentials() {
        var email = renderer.renderAdmissionConfirmationEmail(SendAdmissionConfirmationEmailCommand.builder()
                .username("CNTT080101")
                .build());
        assertThat(email.subject()).isEqualTo("EduHub - Xác nhận nhập học");
        assertThat(email.htmlBody())
                .contains("CNTT080101", "mật khẩu hiện tại", "không bị đặt lại", "tài khoản đang bị khóa")
                .doesNotContain("12345", "Mật khẩu khởi tạo", "Mật khẩu tạm thời");
        assertThat(email.textBody()).contains("CNTT080101", "mật khẩu hiện tại").doesNotContain("12345");
    }

    @Test
    void escapesCredentialsInHtmlWhileKeepingPlainTextReadable() {
        String username = "<img src=x onerror=alert(1)>";
        String password = "p<>&\"'é";
        var email = renderer.renderAccountEmail(SendAccountEmailCommand.builder()
                .username(username)
                .temporaryPassword(password)
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());
        assertThat(email.htmlBody()).contains("&lt;img", "&amp;", "&quot;").doesNotContain("<img src=x", password);
        assertThat(email.textBody()).contains(username, password);
    }

    @Test
    void absentLoginAndSupportConfigurationDoesNotExposePlaceholderLinks() {
        settings.setLoginUrl("");
        settings.setSupportEmail("");
        var email = renderer.renderAdmissionConfirmationEmail(SendAdmissionConfirmationEmailCommand.builder()
                .username("CNTT080101")
                .build());
        assertThat(email.htmlBody()).doesNotContain("href=", "example.com", "Đăng nhập hệ thống");
        assertThat(email.textBody())
                .contains("truy cập hệ thống của nhà trường")
                .doesNotContain("example.com", "null");
    }

    @Test
    void requiresAnAccountTemplateTypeInsteadOfInferringFromUsername() {
        var command = SendAccountEmailCommand.builder()
                .username("CNTT080101")
                .temporaryPassword("12345")
                .build();
        assertThatThrownBy(() -> renderer.renderAccountEmail(command)).isInstanceOf(IllegalArgumentException.class);
    }
}
