package com.eduhub.edu_hub.notification.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
@EnableConfigurationProperties(NotificationMailSettings.class)
public class NotificationTemplateConfiguration {
    @Bean("notificationEmailTemplateEngine")
    public TemplateEngine notificationEmailTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(resolver(TemplateMode.HTML, "mail/*.html", 1));
        engine.addTemplateResolver(resolver(TemplateMode.TEXT, "mail/*.txt", 2));
        return engine;
    }

    private ClassLoaderTemplateResolver resolver(TemplateMode mode, String pattern, int order) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setOrder(order);
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);
        return resolver;
    }
}
