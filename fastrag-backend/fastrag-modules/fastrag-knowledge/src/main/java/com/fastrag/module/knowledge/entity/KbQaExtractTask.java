package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_qa_extract_task") public class KbQaExtractTask {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,sourceType,sourceIds,llmModel,result,params,status,createdBy;
    private Integer totalCount,completedCount;
    private LocalDateTime createdAt,completedAt;
}
