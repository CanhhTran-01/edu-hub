package com.eduhub.edu_hub.curriculum.domain.policy;

import com.eduhub.edu_hub.curriculum.application.exception.CurriculumCodeException;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import com.eduhub.edu_hub.shared.systemconfig.domain.policy.CodePrefixMappingPolicy;
import java.util.Map;

/** Ghép mã CTĐT theo ngành–khóa–hệ từ mapping đã tải; không truy vấn danh mục hoặc cấu hình. */
public final class CurriculumCodePolicy {
    private CurriculumCodePolicy() {}

    // TODO(CURRICULUM_CODE_CREATION): Chưa có luồng tạo CTĐT; khi triển khai, service tải khóa/mapping,
    // kiểm tra duy nhất ngành–khóa–hệ rồi gọi policy và lưu mã. Không đổi mã CTĐT lịch sử khi sửa cấu hình.
    public static String generateCode(
            String majorName,
            String cohortCode,
            TrainingType trainingType,
            Map<String, String> majorPrefixes,
            Map<String, String> trainingTypePrefixes) {
        if (cohortCode == null || !cohortCode.matches("K[1-9][0-9]?") || trainingType == null)
            throw new CurriculumCodeException();
        String majorPrefix = CodePrefixMappingPolicy.requirePrefix(
                majorPrefixes,
                SystemConfigKey.MAJOR_CODE_PREFIXES,
                CodePrefixMappingPolicy.lookupKeyFromName(majorName));
        String trainingTypePrefix = CodePrefixMappingPolicy.requirePrefix(
                trainingTypePrefixes, SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES, trainingType.name());
        return "CTDT-" + majorPrefix + "-" + cohortCode + "-" + trainingTypePrefix;
    }
}
