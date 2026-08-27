package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;

/**
 * 元数据字段定义（AttrDef, 分册四 rag-file-metadata-management.md · schema 驱动）。
 *
 * <p>KB 级元数据 schema 的单个字段定义（落库于 kb.custom_attr_schema 的 JSON 数组元素），
 * 文件级取值存于 kb_file 的 {@code values}（按字段 name 为 key；检索感知字段额外投影到强类型列）。</p>
 *
 * <p>字段均为用户自定义（方案 A：无硬编码固定字段区）。勾选 {@code searchable} 且 name 命中
 * 已知检索维度（region / publishDate / docLevel）时，保存取值的同时投影到 kb_file 对应强类型列，
 * 供检索（分册二）原生 WHERE / ORDER BY。issuer、docNumber 等为普通字段，不入投影列。</p>
 *
 * <p>支持类型：text / number / select / date / boolean / region（地域多值）。</p>
 */
@Data
public class AttrDef {
    /** 字段名（值映射的 key，在同一 KB 内唯一；region/publishDate/docLevel 为检索感知保留名） */
    private String name;
    /** 展示名 */
    private String label;
    /** 字段类型：text / number / select / date / boolean / region */
    private String type;
    /** 候选选项（仅 select 类型使用） */
    private List<String> options;
    /** 是否为必填 */
    private Boolean required;
    /** 描述 */
    private String description;
    /** 是否为检索感知字段（勾选后按保留名投影到强类型列，供检索过滤/排序） */
    private Boolean searchable;
    /** 是否内置（内置检索维度，避免被误删；可空） */
    private Boolean builtin;
    /** 默认值 */
    private Object defaultValue;
}