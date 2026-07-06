package com.fastrag.module.agent.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a controller parameter that should be resolved to the current authenticated user.
 * TODO: Implement a HandlerMethodArgumentResolver to resolve this from the Spring Security context.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
