package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// term_record 只读行实体（表由 platform 模块管理，此处仅供查询增强引擎读取别名扩展）
@Data @TableName("term_record") public class TermRecordRow {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String libraryId,term,alias,definition,category;
    private LocalDateTime createdAt;
}
