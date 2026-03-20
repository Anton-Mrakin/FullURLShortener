package com.mrakin.usecases;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to trigger URL accessed ClickHouse event.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlAccessedClickHouseEvent {
    /**
     * SpEL expression for the short code of the URL.
     */
    String shortCode() default "";
}
