package com.fastrag.module.agent.context;

import lombok.Data;

import java.util.List;

/**
 * Agent配置可配置项数据模型，描述一个可配置字段的完整元信息，用于前端配置界面动态渲染。
 *
 * <p>核心职责：
 * <ul>
 *   <li>封装单个配置字段的展示信息，包括字段名、显示名称、类型、分类、描述、默认值、选项列表和权限要求</li>
 *   <li>由{@link BaseContext#getConfigurableItems(String)}方法根据{@link ConfigField}注解自动生成</li>
 *   <li>通过REST API返回给前端，供配置界面动态渲染表单控件</li>
 * </ul></p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>field - 对应BaseContext中的Java字段名</li>
 *   <li>name - 前端展示的显示名称（如"系统提示词"、"模型选择"）</li>
 *   <li>type - 字段类型（string、list、number），决定渲染的控件类型</li>
 *   <li>kind - 字段分类（prompt、llm、tools等），用于分组展示</li>
 *   <li>auth - 权限标识（如"admin"表示仅管理员可见可配）</li>
 *   <li>defaultValue - 字段默认值</li>
 *   <li>options - 下拉选项列表（Option内部类包含key、name、description）</li>
 * </ul></p>
 *
 * @see BaseContext#getConfigurableItems(String) 生成可配置项的方法
 * @see ConfigField 配置字段注解
 */
@Data
public class ConfigurableItem {

    private String field;

    private String name;

    private String type;

    private String kind;

    private String description;

    private Object defaultValue;

    private List<Option> options;

    private String auth;

    @Data
    public static class Option {

        private String key;

        private String name;

        private String description;
    }
}
