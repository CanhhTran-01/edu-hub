package com.eduhub.edu_hub.scheduling.infrastructure.persistence;

import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Lưu bản chụp có kiểu và dấu SHA-256; không phụ thuộc biểu diễn entity/proxy JPA. */
@Component
@RequiredArgsConstructor
public class TimetablePlanCodec {
    private final ObjectMapper objectMapper;

    // Plan cũ: băm lại dữ liệu hiện tại, khác fingerprint đã lưu nghĩa là lớp/giảng viên/phòng đã đổi.
    // Tránh tính trùng: đầu vào y hệt thì fingerprint y hệt, không cần chạy solver lại.

    public String write(Object content) {
        return objectMapper.writeValueAsString(content);
    }

    public <T> T read(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }

    public String fingerprint(TimetableProblem problem) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(write(problem).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM thiếu thuật toán SHA-256 bắt buộc", exception);
        }
    }
}
