package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_test_case") public class BpmFlowTestCase {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String flowDefId,versionId,name,inputs,expectedOutput,actualOutput;
    private Boolean matchResult;
    private LocalDateTime lastRunAt;
    private String lastInstanceId,createdBy;
    private LocalDateTime createdAt;
}