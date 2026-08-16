package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签关联实体，对应 kb_tag_relation 表。
 *
 * <p>建立标签与被标记目标之间的多对多关联关系。targetType标识目标类型
 * （如知识库、文件等），targetId为目标对象的ID。通过该表可以查询
 * 某个标签下的所有目标，或某个目标的所有标签。</p>
 */
@Data
@TableName("kb_tag_relation")
public class KbTagRelation {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String tagId;
    private String targetType;
    private String targetId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
