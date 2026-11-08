package com.eduhub.edu_hub.notification.infrastructure.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class NotificationEmailExecutionConfiguration {

    @Bean(name = {"notificationMailExecutor", "admissionMailExecutor"})
    public ThreadPoolTaskExecutor notificationMailExecutor() {

        // khởi tạo
        ThreadPoolTaskExecutor emailExecutor = new ThreadPoolTaskExecutor();

        // cấu hình
        emailExecutor.setCorePoolSize(2);
        emailExecutor.setMaxPoolSize(4);
        emailExecutor.setQueueCapacity(1000);
        emailExecutor.setThreadNamePrefix("notification-mail-");

        // Âm thầm bỏ tác vụ bị từ chối khi quá tải hoặc executor đã tắt.
        // Thông báo vẫn được lưu trong cơ sở dữ liệu ở trạng thái chờ;
        // bộ xử lý định kỳ sẽ quét và thử gửi lại.
        emailExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());

        // Khi ứng dụng tắt, tiếp tục xử lý các tác vụ đã nhận.
        // nhưng không đảm bảo tất cả sẽ hoàn thành
        emailExecutor.setWaitForTasksToCompleteOnShutdown(true);

        // Spring chỉ chờ tối đa 15 giây.
        // Hết thời gian này, quá trình đóng ứng dụng tiếp tục;
        // tác vụ chưa xong có thể bị gián đoạn khi tiến trình kết thúc
        emailExecutor.setAwaitTerminationSeconds(15);

        return emailExecutor;
    }
}
