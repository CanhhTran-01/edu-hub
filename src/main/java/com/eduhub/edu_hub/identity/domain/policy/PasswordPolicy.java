package com.eduhub.edu_hub.identity.domain.policy;

import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PasswordPolicy {
    public static final int MAX_PASSWORD_BYTES = 72;
    private final int minimumLength;

    public PasswordPolicy(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    /** Luôn áp dụng cho mật khẩu người dùng tự đặt, kể cả lần đổi mật khẩu đầu tiên. */
    public void validate(String password, String confirmation, String username) {
        List<FieldErrorDetail> errors = new ArrayList<>();

        if (password == null || password.isBlank()) {
            throw new PasswordValidationException(
                    List.of(new FieldErrorDetail("newPassword", "Mật khẩu mới không được để trống")));
        }
        if (password.codePointCount(0, password.length()) < minimumLength) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất " + minimumLength + " ký tự"));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu không được vượt quá 72 byte UTF-8"));
        }

        if (password.codePoints().noneMatch(Character::isUpperCase)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ hoa"));
        }

        if (password.codePoints().noneMatch(Character::isLowerCase)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ thường"));
        }

        if (password.codePoints().noneMatch(Character::isDigit)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 chữ số"));
        }

        if (password.codePoints()
                .noneMatch(c ->
                        !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !Character.isSpaceChar(c))) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 1 ký tự đặc biệt"));
        }

        if (password.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu không được chứa khoảng trắng"));
        }

        if (!password.equals(confirmation)) {
            errors.add(new FieldErrorDetail("confirmPassword", "Xác nhận mật khẩu không khớp"));
        }

        if (password.equals(username)) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu mới không được trùng username/MSSV"));
        }

        if (!errors.isEmpty()) throw new PasswordValidationException(errors);
    }

    public void validateCurrentPasswordInput(String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new PasswordValidationException(
                    List.of(new FieldErrorDetail("currentPassword", "Mật khẩu hiện tại không được để trống")));
        }
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new PasswordValidationException(
                    List.of(new FieldErrorDetail("currentPassword", "Mật khẩu không được vượt quá 72 byte UTF-8")));
        }
    }

    public void validatePasswordReuse(boolean matchesCurrentPassword, boolean matchesPreviousOfficialPassword) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (matchesCurrentPassword) {
            errors.add(new FieldErrorDetail("newPassword", "Mật khẩu mới không được trùng mật khẩu hiện tại"));
        }
        if (matchesPreviousOfficialPassword) {
            errors.add(new FieldErrorDetail(
                    "newPassword", "Mật khẩu mới không được trùng mật khẩu chính thức ngay trước mật khẩu hiện tại"));
        }
        if (!errors.isEmpty()) throw new PasswordValidationException(errors);
    }
}
