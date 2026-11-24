package com.eduhub.edu_hub.shared.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class EntityModuleBoundaryTest {
    private static final String BASE_PACKAGE = "com.eduhub.edu_hub";

    @Test
    void entityReferencesRemainInsideTheOwningModule() throws Exception {
        // Chỉ quét metadata class; không dựng Spring context, SessionFactory hoặc kết nối database.
        var entityScanner = new ClassPathScanningCandidateComponentProvider(false);
        entityScanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        var entityDefinitions = entityScanner.findCandidateComponents(BASE_PACKAGE);
        assertThat(entityDefinitions).isNotEmpty();
        for (var entityDefinition : entityDefinitions) {
            var entityType = Class.forName(
                    entityDefinition.getBeanClassName(), false, getClass().getClassLoader());
            for (var entityField : entityType.getDeclaredFields()) {
                referencedEntityTypes(entityField.getGenericType()).forEach(referencedEntity -> assertThat(
                                owningModule(referencedEntity))
                        .as(
                                "%s.%s không được giữ entity %s của module khác",
                                entityType.getSimpleName(), entityField.getName(), referencedEntity.getSimpleName())
                        .isEqualTo(owningModule(entityType)));
            }
        }
    }

    private Stream<Class<?>> referencedEntityTypes(Type fieldType) {
        if (fieldType instanceof Class<?> fieldClass)
            return fieldClass.isAnnotationPresent(Entity.class) ? Stream.of(fieldClass) : Stream.empty();
        if (fieldType instanceof ParameterizedType parameterizedType)
            return Stream.of(parameterizedType.getActualTypeArguments()).flatMap(this::referencedEntityTypes);
        return Stream.empty();
    }

    private String owningModule(Class<?> entityType) {
        return entityType.getPackageName().substring(BASE_PACKAGE.length() + 1).split("\\.")[0];
    }
}
