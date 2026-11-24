package com.eduhub.edu_hub.curriculum.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.TrainingTypeResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingTypeQueryService {
    private final SystemConfigService systemConfigService;

    public List<TrainingTypeResponse> findTrainingTypes() {
        var prefixes = systemConfigService.getSnapshot().getCodePrefixes(SystemConfigKey.TRAINING_TYPE_CODE_PREFIXES);
        // Mapping rỗng không làm mất danh sách enum; chỉ luồng cấp mã mới yêu cầu đủ tiền tố.
        return Arrays.stream(TrainingType.values())
                .map(trainingType -> {
                    String prefix = prefixes.get(trainingType.name());
                    String label =
                            switch (trainingType) {
                                case CHINH_QUY -> "Chính quy";
                                case LIEN_THONG -> "Liên thông";
                                case VAN_BANG_2 -> "Văn bằng 2";
                            };
                    return new TrainingTypeResponse(trainingType, label, prefix, prefix != null);
                })
                .toList();
    }
}
