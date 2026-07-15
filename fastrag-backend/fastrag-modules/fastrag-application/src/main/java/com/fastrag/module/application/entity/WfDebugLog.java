package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_debug_log") public class WfDebugLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String workflowId, nodeKey, level, message;
    private String context;
    private LocalDateTime createdAt;
}
