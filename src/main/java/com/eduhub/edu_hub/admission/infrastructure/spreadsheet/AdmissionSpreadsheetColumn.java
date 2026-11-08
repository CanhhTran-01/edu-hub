package com.eduhub.edu_hub.admission.infrastructure.spreadsheet;

import com.eduhub.edu_hub.shared.text.TextsNormalizer;

enum AdmissionSpreadsheetColumn {
    APPLICATION_CODE("Mã hồ sơ", "ma_ho_so"),
    CITIZEN_ID("CCCD", "cccd"),
    FULL_NAME("Họ tên", "ho_ten"),
    ENTRANCE_SCORE("Điểm đầu vào", "diem_dau_vao"),
    DATE_OF_BIRTH("Ngày sinh", "ngay_sinh"),
    GENDER("Giới tính", "gioi_tinh"),
    EMAIL("Email", "email"),
    PHONE_NUMBER("Số điện thoại", "so_dien_thoai");

    private final String headerTitle;
    private final String normalizedHeaderTitle;
    private final String normalizedLegacyHeader;

    AdmissionSpreadsheetColumn(String headerTitle, String legacyHeader) {
        this.headerTitle = headerTitle;
        this.normalizedHeaderTitle = TextsNormalizer.normalizeKey(headerTitle);
        this.normalizedLegacyHeader = TextsNormalizer.normalizeKey(legacyHeader);
    }

    String headerTitle() {
        return headerTitle;
    }

    static AdmissionSpreadsheetColumn fromHeaderTitle(String title) {
        String normalizedTitle = TextsNormalizer.normalizeKey(title);
        for (AdmissionSpreadsheetColumn column : values()) {
            if (column.normalizedHeaderTitle.equals(normalizedTitle)
                    || column.normalizedLegacyHeader.equals(normalizedTitle)) {
                return column;
            }
        }
        return null;
    }
}
