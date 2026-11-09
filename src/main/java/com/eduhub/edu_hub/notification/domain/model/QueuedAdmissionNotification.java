package com.eduhub.edu_hub.notification.domain.model;

import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "admission_notifications")
@Builder
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QueuedAdmissionNotification extends BaseEntity {

    // TODO(NOTIFICATION_STORAGE): Khi mở rộng hàng đợi sang nguồn khác, thiết kế sourceType/sourceId
    //  và migration chuyển dữ liệu riêng; không tạo StudentAdmission giả cho luồng không nhập học.
    // Tạo SV đơn lẻ và tạo/gửi lại tài khoản GV hiện chỉ trả kết quả ngay, chưa lưu lâu dài hoặc retry tự động.
    // Khi bổ sung theo dõi/retry các nguồn đó, cần contract nguồn thực tế và facade tương ứng; không thể khôi phục
    // các lần gửi chưa lưu hoặc suy lịch sử từ hồ sơ. Giữ bản ghi nhập học này cho scheduler, không xóa bộ đếm/lỗi.

    @Column(name = "admission_id", nullable = false, unique = true)
    private Long admissionId; // Giữ FK SQL hiện có, không giữ quan hệ JPA sang entity của admission

    @Column(name = "student_id")
    private Long studentId;

    private String recipient;

    @Enumerated(EnumType.STRING)
    private AdmissionNotificationKind kind;

    @Enumerated(EnumType.STRING)
    private NotificationStatus state;

    // Một lần đầu + tối đa ba retry do scheduler; dừng tại bốn thất bại, không reset bộ đếm.
    private int failureCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
}
