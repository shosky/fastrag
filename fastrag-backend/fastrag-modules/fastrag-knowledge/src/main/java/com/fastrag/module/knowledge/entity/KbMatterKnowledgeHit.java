package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// 事项知识命中埋点：座席选定事项后的查看/点开/建单行为，按窗口（默认 30 天）聚合出事项热点知识前 N 个
@Data @TableName("kb_matter_knowledge_hit") public class KbMatterKnowledgeHit {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,matterCode,matterName,knowledgeId,hitType,userId;
    private LocalDateTime createdAt;
}
