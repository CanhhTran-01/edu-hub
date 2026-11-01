package com.eduhub.edu_hub.shared.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**") // 1. Áp dụng CORS cho tất cả các endpoints
                .allowedOriginPatterns("*") // 2. Chỉ định rõ các domain FE được phép gọi API
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 3. Các HTTP Method được phép gọi
                .allowedHeaders("*") // 4. Cho phép tất cả các header trong request
                .allowCredentials(true) // 5. Cho phép Frontend gửi thông tin xác thực
                .maxAge(3600); // 6. Time lưu cache cho "Preflight Request"
    }
}
