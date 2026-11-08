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
public class EmailTemplateConfiguration {
    @Bean("notificationEmailTemplateEngine")
    public TemplateEngine notificationEmailTemplateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(createTemplateResolver(TemplateMode.HTML, "mail/*.html", 1));
        templateEngine.addTemplateResolver(createTemplateResolver(TemplateMode.TEXT, "mail/*.txt", 2));
        return templateEngine;
    }

    private ClassLoaderTemplateResolver createTemplateResolver(
            TemplateMode templateMode, String resolvablePattern, int resolverOrder) {
        var templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setResolvablePatterns(Set.of(resolvablePattern));
        templateResolver.setTemplateMode(templateMode);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templateResolver.setOrder(resolverOrder);
        templateResolver.setCheckExistence(true);
        templateResolver.setCacheable(true);
        return templateResolver;
    }
}
