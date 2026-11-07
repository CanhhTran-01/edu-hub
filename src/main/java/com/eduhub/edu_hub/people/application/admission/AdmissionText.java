package com.eduhub.edu_hub.people.application.admission;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

public final class AdmissionText {
    private AdmissionText() {}

    public static String optional(String value) {
        if (value == null) return null;
        String result = Normalizer.normalize(value, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
        return result.isEmpty() ? null : result;
    }

    public static String key(String value) {
        String normalized = optional(value);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    public static String email(String value) {
        String result = optional(value);
        return result == null ? null : result.toLowerCase(Locale.ROOT);
    }

    public static String citizenId(String value) {
        String result = optional(value);
        return result == null ? null : result.replaceAll("\\s+", "");
    }

    public static Comparator<String> nameOrder() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.SECONDARY);
        return Comparator.comparing(AdmissionText::givenName, collator).thenComparing(AdmissionText::key, collator);
    }

    private static String givenName(String value) {
        String normalized = key(value);
        return normalized.substring(normalized.lastIndexOf(' ') + 1);
    }
}
