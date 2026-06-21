package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_training") public class ModelTraining {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String modelId,modelName,dataset,status,metrics,createdBy;
    private Integer epochs;
    private Long duration;
    private LocalDateTime createdAt;
}
