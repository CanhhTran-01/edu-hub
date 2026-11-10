package com.eduhub.edu_hub.identity.application.specification;

import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện tìm kiếm tài khoản Identity; không truy vấn hồ sơ hoặc trạng thái nghiệp vụ của People. */
public final class UserAccountSpecification {
    private UserAccountSpecification() {}

    public static Specification<User> notDeleted() {
        return (root, query, builder) -> builder.isFalse(root.get("deleted"));
    }

    public static Specification<User> matchesKeyword(String keyword) {
        return (root, query, builder) -> {
            if (keyword == null || keyword.isBlank()) return null;

            // Tìm chuỗi con nguyên văn, không để ký tự % hoặc _ trong đầu vào mở rộng điều kiện LIKE.
            String keywordPattern = "%"
                    + keyword.trim()
                            .toLowerCase(Locale.ROOT)
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_")
                    + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("username")), keywordPattern, '\\'),
                    builder.like(builder.lower(root.get("email")), keywordPattern, '\\'));
        };
    }

    public static Specification<User> hasRole(RoleName role) {
        return (root, query, builder) ->
                role == null ? null : builder.equal(root.get("role").get("roleName"), role.name());
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    public static Specification<User> requiresPasswordChange(Boolean firstLogin) {
        // false là một bộ lọc có giá trị, khác với null là bỏ lọc.
        return (root, query, builder) -> firstLogin == null ? null : builder.equal(root.get("firstLogin"), firstLogin);
    }
}
