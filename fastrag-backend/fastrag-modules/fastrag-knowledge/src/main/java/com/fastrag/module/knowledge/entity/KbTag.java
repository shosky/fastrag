package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库标签实体，对应 kb_tag 表。
 *
 * <p>用于知识库和文档的标签管理，包含标签名称、颜色、描述和使用次数。
 * 标签通过 {@link KbTagRelation} 关联到知识库或文件等目标对象。</p>
 *
 * <p>字段说明：{@code kbId} 为创建标签时的归属知识库ID，{@code tagTypeId}
 * 为标签类型（如T1文档分类/T2优先级/T3状态，可空）。</p>
 */
@Data
@TableName("kb_tag")
public class KbTag {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 归属知识库ID（创建标签时的归属KB，NOT NULL） */
    private String kbId;
    /** 标签类型ID（T1文档分类/T2优先级/T3状态，可空） */
    private String tagTypeId;
    private String name;
    private String color;
    private String description;
    private Integer usageCount;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
