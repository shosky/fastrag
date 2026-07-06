package com.fastrag.module.agent.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigField {

    String name() default "";

    String description() default "";

    String type() default "string";

    String kind() default "";

    String auth() default "";

    boolean configurable() default true;

    boolean hide() default false;
}
