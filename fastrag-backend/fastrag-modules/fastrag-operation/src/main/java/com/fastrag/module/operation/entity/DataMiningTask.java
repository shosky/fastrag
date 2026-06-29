package com.fastrag.module.operation.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("data_mining_task") public class DataMiningTask {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,kbId,ruleType,ruleConfig,status,resultSummary,creator;
    private LocalDateTime lastRunAt,createdAt,updatedAt;
}
