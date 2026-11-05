package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {
    public void validate(String password, String confirmation, boolean matchesCurrentPassword) {

        List<FieldErrorDetail> errors = new ArrayList<>();

        if (password.length() < 8) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 8 ký tự"));
        }

        if (password.chars().noneMatch(Character::isUpperCase)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ hoa"));
        }

        if (password.chars().noneMatch(Character::isLowerCase)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ thường"));
        }

        if (password.chars().noneMatch(Character::isDigit)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ số"));
        }

        if (password.chars()
                .noneMatch(c ->
                        !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !Character.isSpaceChar(c))) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 ký tự đặc biệt"));
        }

        if (password.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu không được chứa khoảng trắng"));
        }

        if (!password.equals(confirmation)) {
            errors.add(new FieldErrorDetail("confirmPassword", "Xác nhận mật khẩu không khớp"));
        }

        if (matchesCurrentPassword) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu mới không được trùng mật khẩu hiện tại"));
        }

        if (!errors.isEmpty()) throw new PasswordValidationException(errors);
    }
}
