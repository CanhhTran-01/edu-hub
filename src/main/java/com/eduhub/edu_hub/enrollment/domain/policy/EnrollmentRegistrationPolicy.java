package com.eduhub.edu_hub.enrollment.domain.policy;

import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.*;

/** Các quy tắc thuần; dữ liệu, khóa và transaction do application service cung cấp. */
public final class EnrollmentRegistrationPolicy {
    private EnrollmentRegistrationPolicy() {}

    public static void requireRegistrationWindow(LocalDate start, LocalDate end, boolean locked, LocalDate today) {
        if (locked || today.isBefore(start) || today.isAfter(end))
            throw new EnrollmentException(ErrorCode.REGISTRATION_CLOSED);
    }

    public static void requireSubjectAvailable(Long subjectId, List<Enrollment> history) {
        if (history.stream()
                .anyMatch(enrollment -> enrollment.getSubjectId().equals(subjectId)
                        && enrollment.getStatus() == EnrollmentStatus.COMPLETED))
            throw new EnrollmentException(ErrorCode.ENROLLMENT_SUBJECT_COMPLETED);
        if (history.stream()
                .anyMatch(enrollment -> enrollment.getSubjectId().equals(subjectId)
                        && enrollment.getStatus() == EnrollmentStatus.REGISTERED
                        && !enrollment.isDeleted())) throw new EnrollmentException(ErrorCode.ALREADY_ENROLLED);
    }

    public static Set<Long> passedSubjectIds(List<Enrollment> history) {
        Set<Long> passedSubjects = new HashSet<>();
        for (var enrollment : history)
            if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) passedSubjects.add(enrollment.getSubjectId());
        return passedSubjects;
    }

    public static int registeredCredits(List<Enrollment> history, Long semesterId) {
        return history.stream()
                .filter(enrollment -> enrollment.getSemesterId().equals(semesterId)
                        && !enrollment.isDeleted()
                        && enrollment.getStatus() == EnrollmentStatus.REGISTERED)
                .mapToInt(Enrollment::getCredits)
                .sum();
    }

    public static void requireWithinCreditLimit(int registeredCredits, int subjectCredits, int maximumCredits) {
        if ((long) registeredCredits + subjectCredits > maximumCredits)
            throw new EnrollmentException(
                    ErrorCode.ENROLLMENT_CREDIT_LIMIT_EXCEEDED,
                    List.of(new FieldErrorDetail(
                            "moduleClassId",
                            "Tổng tín chỉ sau đăng ký: " + ((long) registeredCredits + subjectCredits)
                                    + "; giới hạn tối đa: " + maximumCredits)));
    }

    public static List<String> minimumCreditWarnings(int registeredCredits, int minimumCredits) {
        return registeredCredits < minimumCredits
                ? List.of("Tổng tín chỉ đã đăng ký: " + registeredCredits + "; chưa đạt mức tối thiểu " + minimumCredits
                        + ".")
                : List.of();
    }
}
