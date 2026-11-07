package com.eduhub.edu_hub.people.infrastructure.admission;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class AdmissionMailConfiguration {
    @Bean(name = "admissionMailExecutor")
    public ThreadPoolTaskExecutor admissionMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("admission-mail-");

        // Âm thầm bỏ tác vụ bị từ chối khi quá tải hoặc executor đã tắt.
        // Thông báo vẫn được lưu trong cơ sở dữ liệu ở trạng thái chờ;
        // bộ xử lý định kỳ sẽ quét và thử gửi lại.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        // Khi ứng dụng tắt, tiếp tục xử lý các tác vụ đã nhận.
        // nhưng không đảm bảo tất cả sẽ hoàn thành
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Spring chỉ chờ tối đa 15 giây.
        // Hết thời gian này, quá trình đóng ứng dụng tiếp tục;
        // tác vụ chưa xong có thể bị gián đoạn khi tiến trình kết thúc
        executor.setAwaitTerminationSeconds(15);

        return executor;
    }
}
