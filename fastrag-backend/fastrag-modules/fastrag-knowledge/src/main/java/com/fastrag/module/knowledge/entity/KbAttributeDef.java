package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 知识库自定义属性定义（应答知识库-属性管理/添加属性） */
@Data @TableName("kb_attribute_def") public class KbAttributeDef {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name;
    private String attrType; // text / number / date / select
    private Integer required;
    private String description;
    private LocalDateTime createdAt;
}
