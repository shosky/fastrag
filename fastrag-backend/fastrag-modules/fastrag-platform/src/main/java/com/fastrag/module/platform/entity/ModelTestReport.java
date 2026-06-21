package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_test_report") public class ModelTestReport {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String modelId,modelName,testDataset,score,metrics,status;
    private Integer testCount;
    private LocalDateTime createdAt;
}
