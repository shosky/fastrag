package com.fastrag.module.knowledge.entity;
/**
 * 知识库文件夹实体类，对应数据库表 kb_folder。
 *
 * <p>核心职责：
 * 表示知识库内的文件夹结构，通过 parentId 字段构建树形层级关系。
 * 文件夹用于对知识库中的文件进行分组组织，每个文件夹属于唯一知识库（kbId），
 * 支持同级排序（sort 字段）。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>name — 文件夹名称</li>
 *   <li>parentId — 父文件夹 ID，空值表示根级文件夹</li>
 *   <li>sort — 同级排序权重，值越小越靠前</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_folder")
public class KbFolder {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String name;
    private String parentId;
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
