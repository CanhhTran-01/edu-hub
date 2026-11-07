package com.eduhub.edu_hub.people.application.code;

import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class AdministrativeClassNames {
    private static final Pattern LEGACY = Pattern.compile("^L([0-9]{1,2})$");
    private static final Pattern CURRENT = Pattern.compile("^[A-Z]+([1-9][0-9]?)([A-Z]+)$");

    private AdministrativeClassNames() {}

    public static String prefix(String majorName) {

        if (majorName == null) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);

        String plain = Normalizer.normalize(majorName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toUpperCase(Locale.ROOT);

        StringBuilder initials = new StringBuilder();

        for (String word : plain.split("[^A-Z]+")) {
            if (!word.isEmpty()) initials.append(word.charAt(0));
        }

        if (initials.isEmpty()) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_MAJOR);

        // TODO(CLASS_PREFIX_CONFIG): Bổ sung cấu hình ghi đè tiền tố theo từng ngành trong cấu hình hệ thống.
        //  Hiện tại vẫn tiếp tục tự suy ra tiền tố; nếu trùng tiền tố giữa các ngành thì chỉ cảnh báo, không được chặn.
        //  Việc ghi đè/đổi tên ngành trong tương lai không được âm thầm đổi tên các lớp hiện có hoặc làm thay đổi MSSV.

        return initials.length() == 1
                ? initials.toString()
                : "" + initials.charAt(0) + initials.charAt(initials.length() - 1);
    }

    public static String create(String majorName, int cohortSequence, int classSequence) {
        if (cohortSequence < 1 || cohortSequence > 99) throw new BaseException(ErrorCode.INVALID_STUDENT_CODE_COHORT);
        return prefix(majorName) + cohortSequence + suffix(classSequence);
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
