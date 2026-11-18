package com.eduhub.edu_hub;

import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "applicationDateTimeProvider")
public class EduHubApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.APPLICATION_ZONE));
        SpringApplication.run(EduHubApplication.class, args);
    }
}
