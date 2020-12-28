package tech.toparvion.analog.service.choice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Polyudov
 * @since v0.14
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@SuppressWarnings("DefaultAnnotationParam")
@ConditionalOnProperty(name = "choices-source.auto-reload-enabled", havingValue = "true", matchIfMissing = false)
public @interface ConditionalOnChoicesAutoReloadEnabled {}