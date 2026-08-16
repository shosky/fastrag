package com.fastrag.module.knowledge.entity;
/**
 * 知识库分类实体类，对应数据库表 kb_category。
 *
 * <p>核心职责：
 * 表示知识库的分类标签，用于对知识库进行分组管理。分类严格按组织隔离，
 * 每个分类归属于一个组织（orgId），平台级 API Token 可管理全量分类。
 * 分类带有排序权重（sort）、颜色（color）和图标（icon）等展示属性。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>name — 分类名称</li>
 *   <li>description — 分类描述</li>
 *   <li>color / icon — 前端展示用的颜色值和图标标识</li>
 *   <li>sort — 排序权重，值越小越靠前</li>
 *   <li>orgId — 归属组织 ID，非空表示该组织私有分类</li>
 *   <li>createdBy — 创建者用户 ID</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_category") public class KbCategory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name;
    private String description;
    private String color;
    private String icon;
    private Integer sort;
    private String orgId; // 归属组织（NULL=未分配，仅管理员可见；非空=该组织私有分类）
    private String createdBy;
    private LocalDateTime createdAt;
}
