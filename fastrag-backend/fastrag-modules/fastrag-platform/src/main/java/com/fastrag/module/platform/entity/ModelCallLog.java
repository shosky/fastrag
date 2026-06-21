package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_call_log") public class ModelCallLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String modelId,caller,status;
    private Integer duration,tokens;
    private LocalDateTime timestamp;
}
