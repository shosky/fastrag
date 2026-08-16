package com.fastrag.module.agent.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置字段元数据注解，用于标记{@link BaseContext}中的可配置字段并声明其UI展示属性。
 *
 * <p>核心职责：
 * <ul>
 *   <li>标注在BaseContext及其子类的字段上，声明该字段是否可配置、在配置界面中如何展示</li>
 *   <li>通过name、description、type、kind等属性定义字段的展示信息</li>
 *   <li>通过auth属性控制字段的可访问权限（如管理员专属配置）</li>
 *   <li>通过hide属性隐藏内部字段，不在配置界面中展示</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>由{@link BaseContext#getConfigurableItems(String)}方法读取此注解信息，生成前端可用的ConfigurableItem列表</li>
 *   <li>由{@link BaseContext#updateFromMap(Map)}方法读取此注解，识别需要从Map中填充的字段</li>
 *   <li>type属性支持：string、list、number等类型</li>
 *   <li>kind属性用于分类：prompt、llm、tools、knowledges、mcps、skills、databases等</li>
 * </ul></p>
 *
 * @see BaseContext 使用此注解的上下文基类
 * @see ConfigurableItem 由注解生成的可配置项数据模型
 */
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
