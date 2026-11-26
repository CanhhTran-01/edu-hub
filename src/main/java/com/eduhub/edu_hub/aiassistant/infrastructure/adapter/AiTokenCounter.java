package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/** Dùng cùng bộ đếm với chunker để giới hạn ngữ cảnh nhất quán; token của provider có thể khác. */
@Component
public class AiTokenCounter {
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    public int countTokens(String text) {
        return encoding.countTokens(text);
    }
}
