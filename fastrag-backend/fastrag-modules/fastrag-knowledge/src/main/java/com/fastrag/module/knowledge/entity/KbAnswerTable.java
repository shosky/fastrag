package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 表格型应答知识（应答知识库-新增表格） */
@Data @TableName("kb_answer_table") public class KbAnswerTable {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,description;
    private LocalDateTime createdAt,updatedAt;
}
