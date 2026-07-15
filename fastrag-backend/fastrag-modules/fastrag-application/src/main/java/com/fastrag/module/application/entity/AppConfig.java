package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.math.BigDecimal;
@Data @TableName("app_config") public class AppConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId,model,prompt,knowledgeIds,toolIds;
    private BigDecimal temperature;
    private Integer maxTurns;
    /** 上下文摘要触发阈值（token数），默认 8000 */
    private Integer summaryThreshold;
    /** 上下文摘要提示词 */
    private String summaryPrompt;
    /** 最大执行步数，默认 15 */
    private Integer maxSteps;
    /** 模型重试次数，默认 2 */
    private Integer retryTimes;
    /** 最大输出 token 数，默认 2048 */
    private Integer maxTokens;
}
