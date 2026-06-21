package com.fastrag.security.annotation;

import com.fastrag.common.enums.KBRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KbAuth {
    KBRole value() default KBRole.viewer;
}
