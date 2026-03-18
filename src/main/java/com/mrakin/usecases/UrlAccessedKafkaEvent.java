package com.mrakin.usecases;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to trigger URL accessed Kafka event.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlAccessedKafkaEvent {
    /**
     * SpEL expression for the key of the Kafka message.
     */
    String key() default "";
}
