package tech.toparvion.analog.service.choice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Custom {@link Conditional @Conditional} that checks if choices <code>auto reload</code> enabled in <code>properties</code>.<br/>
 * This is some kind of "syntax sugar" witch is designed to add this condition to all required places.
 *
 * @author Polyudov
 * @since v0.14
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@SuppressWarnings("DefaultAnnotationParam")
@ConditionalOnProperty(name = "choices-source.auto-reload-enabled", havingValue = "true", matchIfMissing = false)
public @interface ConditionalOnChoicesAutoReloadEnabled {}