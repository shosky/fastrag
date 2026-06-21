package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal;
@Data @TableName("app_config") public class AppConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId,model,prompt,knowledgeIds,toolIds;
    private BigDecimal temperature;
    private Integer maxTurns;
}
