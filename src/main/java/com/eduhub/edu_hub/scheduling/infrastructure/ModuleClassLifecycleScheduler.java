package com.eduhub.edu_hub.scheduling.infrastructure;

import com.eduhub.edu_hub.scheduling.application.service.ModuleClassLifecycleService;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.repository.ModuleClassRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModuleClassLifecycleScheduler {
    private final ModuleClassRepository moduleClassRepository;
    private final ModuleClassLifecycleService lifecycleService;

    @Scheduled(fixedDelayString = "${scheduling.timetabling.lifecycle-delay-ms:60000}")
    public void synchronizeStatuses() {
        // Mỗi kỳ một transaction riêng, lỗi một kỳ không bỏ qua các kỳ còn lại và được thử ở lần quét sau.
        // Chỉ lấy các học kỳ còn lớp OPEN/CLOSED; lớp đã hoàn thành thì không cần quét lại.
        for (Long semesterId : moduleClassRepository.findSemesterIdsForLifecycle(
                List.of(ModuleClassStatus.OPEN, ModuleClassStatus.CLOSED))) {
            try {
                // Transaction nằm trong service, nên mỗi học kỳ commit/rollback độc lập
                lifecycleService.synchronizeSemester(semesterId);
            } catch (RuntimeException failure) {
                // Chỉ log rồi đi tiếp sang học kỳ khác
                // TODO: học kỳ lỗi vẫn còn lớp OPEN/CLOSED nên lần quét sau tự thử lại, xử lý sau
                log.error("Không cập nhật được trạng thái lớp: semesterId={}", semesterId, failure);
            }
        }
    }
}
