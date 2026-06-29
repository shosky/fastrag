package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_monitor_data") public class WfMonitorData {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId,workflowId,dbId,module,nodeKey,nodeType;
    private String name,description,type,status,operator,varType,varKey;
    private String alias,scopeType,scopeValue,configSnapshot,suggestionType;
    private String query,expectedAnswer,actualAnswer,sessionId,level,message,context,triggerType,actionType;
    private String targetEnv,strategy,validateResult,metricType,dimension,details;
    private String canvasData,category,createdBy,config,fallbackText;
    private Integer enabled,priority,hitCount,memoryRounds,maxInputLength,showAvatar;
    private Integer showFeedback,showSuggestions,sort,usageCount,isBuiltin;
    private Integer matched,progress,version,timeoutSeconds,autoPublish;
    private Integer safetyEnabled,unmatchedEnabled;
    private Double metricValue,impactScore,similarity,temperature;
    private LocalDateTime createdAt,updatedAt,publishedAt,periodStart,periodEnd;
}
