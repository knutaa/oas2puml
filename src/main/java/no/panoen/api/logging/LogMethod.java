package no.panoen.api.logging;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import no.panoen.api.logging.AspectLogger.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethod {
	LogLevel level() default LogLevel.DEBUG;
}
