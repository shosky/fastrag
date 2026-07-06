package com.fastrag.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "agent_run", autoResultMap = true)
public class AgentRun {

    @TableId(type = IdType.INPUT)
    private String id;

    private String threadId;

    private String agentId;

    private String uid;

    private String requestId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputPayload;

    private String status;

    private String runType;

    private String parentAgentRunId;

    private Long conversationId;

    private String checkpointThreadId;

    private String errorMessage;

    private String errorType;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;
}
