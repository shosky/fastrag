package com.fastrag.module.publish.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_publish_history") public class KbPublishHistory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,publishType,onlineVersion,offlineVersion,status,operator;
    private Integer version;
    private LocalDateTime scheduledAt,publishedAt,createdAt;
}
