package com.fastrag.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent运行创建请求DTO，用于创建新的Agent运行任务时的请求参数。
 *
 * <p>使用场景：由{@link com.fastrag.module.agent.controller.AgentRunController}的POST /runs接口使用。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>threadId - 会话线程ID（必填），标识Agent运行所属的会话上下文</li>
 *   <li>agentId - Agent标识（必填），指定要运行的Agent（支持slug或ID）</li>
 *   <li>query - 用户输入的查询内容，即本轮对话的用户消息</li>
 *   <li>runType - 运行类型，区分不同的执行模式</li>
 *   <li>modelOverride - 模型覆盖，允许本次运行使用与Agent配置不同的LLM模型</li>
 *   <li>requestId - 请求ID，用于幂等性和请求追踪</li>
 * </ul></p>
 *
 * <p>校验规则：threadId和agentId字段通过@NotBlank确保不为空。</p>
 *
 * @see com.fastrag.module.agent.controller.AgentRunController 使用此DTO的控制器
 * @see com.fastrag.module.agent.entity.AgentRun 对应的运行实体
 */
@Data
public class AgentRunCreateDTO {

    @NotBlank(message = "线程ID不能为空")
    private String threadId;

    @NotBlank(message = "Agent ID不能为空")
    private String agentId;

    private String query;

    private String runType;

    private String modelOverride;

    private String requestId;
}
