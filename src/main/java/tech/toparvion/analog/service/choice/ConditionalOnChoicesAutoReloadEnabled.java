package tech.toparvion.analog.service.choice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * @author Polyudov
 * @since 0.14
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@SuppressWarnings("DefaultAnnotationParam")
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@ConditionalOnProperty(name = "choices.custom.auto-reload-enabled", havingValue = "true", matchIfMissing = false)
@interface ConditionalOnChoicesAutoReloadEnabled {
}