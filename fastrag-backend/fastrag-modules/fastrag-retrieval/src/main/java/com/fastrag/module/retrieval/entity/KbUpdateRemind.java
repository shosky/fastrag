package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_update_remind") public class KbUpdateRemind {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId;
    private Integer enabled;
    private String cronExpr;
    private String channels;
    private LocalDateTime lastRemindAt,createdAt,updatedAt;
}
