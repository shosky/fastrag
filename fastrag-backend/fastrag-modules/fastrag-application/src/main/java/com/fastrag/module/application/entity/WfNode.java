package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_node") public class WfNode {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String workflowId,nodeKey,nodeType,name,config,nextNodes;
    private Integer positionX,positionY,enabled; private LocalDateTime createdAt;
}
