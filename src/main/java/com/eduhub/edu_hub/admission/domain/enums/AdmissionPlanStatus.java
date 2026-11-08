package com.eduhub.edu_hub.admission.domain.enums;

public enum AdmissionPlanStatus {
    DRAFT,
    CLASSES_READY,
    PREVIEWED,
    CONFIRMING, // Đang trong quá trình xác nhận
    PARTIALLY_CONFIRMED, // Xác nhận một phần, một số sinh viên bị lỗi hoặc chưa xử lý
    CONFIRMED
}
