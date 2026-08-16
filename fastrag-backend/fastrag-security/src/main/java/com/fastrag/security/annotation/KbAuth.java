package com.fastrag.security.annotation;

/**
 * 知识库权限控制注解，用于在 Controller 方法上声明所需的最低知识库角色。
 *
 * <p>使用方式：在需要权限控制的知识库接口方法上标注，如 {@code @KbAuth(KBRole.editor)}，
 * 由 {@link com.fastrag.security.aspect.KbAuthAspect} 切面拦截并校验当前用户是否具备足够权限。
 *
 * <p>权限层级：owner(3) > editor(2) > viewer(1)，未标注时默认要求 viewer 角色。
 *
 * <p>与其他模块的交互：该注解被 {@link com.fastrag.security.aspect.KbAuthAspect} 通过
 * {@code @Around("@annotation(kbAuth)")} 切点表达式拦截处理。
 */
import com.fastrag.common.enums.KBRole;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KbAuth {
    KBRole value() default KBRole.viewer;
}
