package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_test_case") public class WfTestCase {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String workflowId,name,query,inputs,expectedOutput,actualOutput,createdBy;
    private Integer matched; private LocalDateTime createdAt;
}
