package com.eduhub.edu_hub.people.application.code;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.CodePrefixMappingPolicy;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class AdministrativeClassNames {
    private static final Pattern LEGACY = Pattern.compile("^L([0-9]{1,2})$");
    private static final Pattern CURRENT = Pattern.compile("^[A-Z]+([1-9][0-9]?)([A-Z]+)$");

    private AdministrativeClassNames() {}

    public static String prefix(String majorName, Map<String, String> majorPrefixes) {
        // Chỉ tra tiền tố cho lớp mới; tên lớp lịch sử vẫn được đọc độc lập với mapping hiện hành.
        return CodePrefixMappingPolicy.requirePrefix(
                majorPrefixes,
                SystemConfigKey.MAJOR_CODE_PREFIXES,
                CodePrefixMappingPolicy.lookupKeyFromName(majorName));
    }

    public static String create(
            String majorName, int cohortSequence, int classSequence, Map<String, String> majorPrefixes) {
        if (cohortSequence < 1 || cohortSequence > 99) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return prefix(majorName, majorPrefixes) + cohortSequence + suffix(classSequence);
    }

    public static String suffix(int sequence) {

        if (sequence < 1 || sequence > 99) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_CLASS);

        StringBuilder suffix = new StringBuilder();
        for (int remaining = sequence; remaining > 0; remaining /= 26) {
            remaining--;
            suffix.append((char) ('A' + remaining % 26));
        }

        return suffix.reverse().toString();
    }

    /** A=1, Z=26, AA=27. Read legacy L01 names without renaming historical records. */
    public static OptionalInt sequence(String className, Integer cohortSequence) {

        if (className == null || cohortSequence == null || cohortSequence < 1 || cohortSequence > 99) {
            return OptionalInt.empty();
        }

        String name = className.toUpperCase(Locale.ROOT);
        var legacy = LEGACY.matcher(name);

        if (legacy.matches()) {
            int sequence = Integer.parseInt(legacy.group(1));
            return sequence > 0 ? OptionalInt.of(sequence) : OptionalInt.empty();
        }

        var current = CURRENT.matcher(name);
        if (!current.matches() || Integer.parseInt(current.group(1)) != cohortSequence) return OptionalInt.empty();

        String suffix = current.group(2);
        int sequence = 0;
        for (int i = 0; i < suffix.length(); i++) {
            sequence = sequence * 26 + suffix.charAt(i) - 'A' + 1;
            if (sequence > 99) return OptionalInt.empty();
        }

        return OptionalInt.of(sequence);
    }
}
