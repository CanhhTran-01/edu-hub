package com.eduhub.edu_hub.aiassistant.domain.policy;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Chuẩn hóa và kiểm tra thuần; đây là lớp chắn mẫu nhạy cảm rõ ràng, không thay việc Admin kiểm duyệt tài liệu. */
public final class AiContentPolicy {
    private static final Pattern CITIZEN_ID = Pattern.compile("(?<![\\p{L}\\p{N}])\\d{12}(?![\\p{L}\\p{N}])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SECRET =
            Pattern.compile("(?iu)(mật\\s*khẩu|password|api[-_ ]?key|access[-_ ]?token|secret)\\s*[:=]\\s*\\S+");
    private static final Pattern CITATION = Pattern.compile("\\[K([^]\\r\\n]*)]");

    private AiContentPolicy() {}

    public static String normalizeQuestion(String question) {
        return Normalizer.normalize(question, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String redactPromptText(String text) {
        String normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC);
        return SECRET.matcher(CITIZEN_ID
                        .matcher(EMAIL.matcher(normalizedText).replaceAll("[đã ẩn email]"))
                        .replaceAll("[đã ẩn định danh]"))
                .replaceAll("[đã ẩn thông tin xác thực]");
    }

    public static boolean containsSensitiveDocumentContent(String text) {
        return CITIZEN_ID.matcher(text).find() || SECRET.matcher(text).find();
    }

    public static boolean hasValidCitations(String content, List<Long> citationIds, Set<Long> suppliedChunkIds) {
        if (content == null
                || content.isBlank()
                || citationIds == null
                || citationIds.isEmpty()
                || citationIds.stream().anyMatch(chunkId -> chunkId == null || !suppliedChunkIds.contains(chunkId))) {
            return false;
        }
        var inlineChunkIds = new HashSet<Long>();
        var matcher = CITATION.matcher(content);
        while (matcher.find()) {
            try {
                long chunkId = Long.parseLong(matcher.group(1));
                if (chunkId <= 0 || !Long.toString(chunkId).equals(matcher.group(1))) return false;
                inlineChunkIds.add(chunkId);
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return inlineChunkIds.equals(new HashSet<>(citationIds));
    }
}
