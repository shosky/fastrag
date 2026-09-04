package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_instance_event") public class BpmFlowInstanceEvent {
    @TableId(type=IdType.AUTO) private Long id;
    private String instanceId,traceId,nodeKey,eventType,level,message,context;
    private String inputSnapshot,outputSnapshot;
    private Integer durationMs;
    private LocalDateTime createdAt;
}