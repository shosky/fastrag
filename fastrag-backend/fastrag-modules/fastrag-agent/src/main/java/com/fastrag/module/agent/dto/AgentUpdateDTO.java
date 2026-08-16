package com.fastrag.module.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * Agent更新请求DTO，用于更新已有Agent信息时的请求参数。
 *
 * <p>使用场景：由{@link com.fastrag.module.agent.controller.AgentController}的PUT /{agentId}接口和
 * {@link com.fastrag.module.agent.controller.AgentConfigController}的PUT /{agentId}/config接口使用。</p>
 *
 * <p>字段映射关系（与{@link com.fastrag.module.agent.entity.Agent}实体的映射，所有字段均为可选，仅更新非null字段）：
 * <ul>
 *   <li>name - Agent名称，对应Agent.name</li>
 *   <li>description - Agent描述，对应Agent.description</li>
 *   <li>icon - Agent图标，对应Agent.icon</li>
 *   <li>configJson - Agent配置JSON，对应Agent.configJson（包含模型、提示词、工具等运行时配置）</li>
 *   <li>shareConfig - 分享配置，对应Agent.shareConfig</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.controller.AgentController 使用此DTO的控制器
 * @see AgentCreateDTO Agent创建DTO
 */
@Data
public class AgentUpdateDTO {

    private String name;

    private String description;

    private String icon;

    private Map<String, Object> configJson;

    private Map<String, Object> shareConfig;
}
