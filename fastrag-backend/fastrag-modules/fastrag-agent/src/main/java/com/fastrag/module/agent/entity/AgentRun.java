package com.fastrag.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent运行记录实体类，对应数据库表agent_run，记录每次Agent运行的完整生命周期数据。
 *
 * <p>核心职责：
 * <ul>
 *   <li>记录Agent每次运行的元数据（所属线程、Agent、用户、状态等）</li>
 *   <li>保存运行输入（inputPayload）和错误信息（errorMessage、errorType）</li>
 *   <li>跟踪运行的生命周期（创建时间、更新时间、完成时间）</li>
 *   <li>支持子Agent运行的关联（parentAgentRunId）</li>
 * </ul></p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>id - 主键，使用INPUT策略（由业务层生成，通常为UUID）</li>
 *   <li>threadId - 会话线程ID，关联到聊天会话</li>
 *   <li>agentId - 关联的Agent标识，指向agent表的id</li>
 *   <li>uid - 创建运行的用户ID</li>
 *   <li>status - 运行状态（如pending、running、completed、failed、cancelled）</li>
 *   <li>runType - 运行类型，区分不同的执行模式</li>
 *   <li>parentAgentRunId - 父运行ID，当此运行为子Agent运行时指向父Agent的运行记录</li>
 *   <li>conversationId - 会话ID，用于关联外部会话系统</li>
 *   <li>checkpointThreadId - 检查点线程ID，用于运行状态恢复</li>
 *   <li>inputPayload - 运行输入负载JSON，存储用户查询和运行参数</li>
 * </ul></p>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>通过AgentRunMapper（MyBatis-Plus）进行数据库CRUD操作</li>
 *   <li>inputPayload字段通过JacksonTypeHandler在JSON与Map之间自动转换</li>
 *   <li>由AgentRunService管理运行生命周期，由AgentRunServiceImpl提供异步执行能力</li>
 *   <li>运行事件通过{@link com.fastrag.module.agent.event.RunEventPublisher}发布，通过SSE推送给前端</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.mapper.AgentRunMapper 数据库映射器
 * @see com.fastrag.module.agent.service.AgentRunService 运行业务服务
 */
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
