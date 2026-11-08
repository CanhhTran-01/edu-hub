package com.eduhub.edu_hub.shared.text;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

public final class TextsNormalizer {
    private TextsNormalizer() {}

    public static String normalizeOptionalText(String value) {
        if (value == null) return null;
        String result = Normalizer.normalize(value, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
        return result.isEmpty() ? null : result;
    }

    public static String normalizeKey(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeEmail(String value) {
        String result = normalizeOptionalText(value);
        return result == null ? null : result.toLowerCase(Locale.ROOT);
    }

    public static String normalizeCitizenId(String value) {
        String result = normalizeOptionalText(value);
        return result == null ? null : result.replaceAll("\\s+", "");
    }

    public static Comparator<String> vietnameseNameOrder() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.SECONDARY);
        return Comparator.comparing(TextsNormalizer::extractGivenName, collator)
                .thenComparing(TextsNormalizer::normalizeKey, collator);
    }

    private static String extractGivenName(String value) {
        String normalized = normalizeKey(value);
        return normalized.substring(normalized.lastIndexOf(' ') + 1);
    }
}
