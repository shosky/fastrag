package com.fastrag.module.application.widget.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Widget 卡片资产（widget 表）：模板/数据结构/默认值三件套定义一张可交互对话卡片。
 *
 * <p>保存即生效（无版本列）；{@code is_builtin=1} 为官方模板行（只读，仅可复制），
 * {@code creator="system"} 标记官方播种来源。列名 {@code schema_json}/{@code default_json}
 * 避开 MySQL 保留字 schema 与 Java 关键字 default。</p>
 */
@Data
@TableName(value = "widget", autoResultMap = true)
public class Widget {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;

    private String description;

    private String icon;

    /** 组件树 JSON：{type,props,children[]}，字符串 prop 支持 {{var}} 插值 */
    private String template;

    /** 变量声明 JSON（JSON Schema 子集：properties/required） */
    @TableField("schema_json")
    private String schemaJson;

    /** 占位数据 JSON（模板变量填充兜底） */
    @TableField("default_json")
    private String defaultJson;

    /** 1=官方模板行（只读，仅可复制） */
    private Integer isBuiltin;

    /** 属主 userId；"system"=官方播种 */
    private String creator;

    private java.time.LocalDateTime createdAt;

    private java.time.LocalDateTime updatedAt;
}
