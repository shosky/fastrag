package com.fastrag.module.agent.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前登录用户参数注解，用于Controller方法中自动注入当前认证用户信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>标注在Controller方法的参数上，声明该参数需要从Spring Security上下文中解析当前登录用户</li>
 *   <li>由{@link com.fastrag.module.agent.config.CurrentUserArgumentResolver}负责解析此注解并注入{@link User}对象</li>
 * </ul></p>
 *
 * <p>使用方式：在Controller方法中声明 @CurrentUser User user 参数，框架自动注入当前用户信息。</p>
 *
 * @see com.fastrag.module.agent.config.CurrentUserArgumentResolver 解析此注解的参数解析器
 * @see User 注入的目标用户类型
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
