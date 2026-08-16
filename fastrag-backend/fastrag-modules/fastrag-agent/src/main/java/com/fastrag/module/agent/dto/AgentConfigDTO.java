package com.fastrag.module.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * Agent配置更新请求DTO，用于Agent配置修改接口的请求参数。
 *
 * <p>使用场景：由{@link com.fastrag.module.agent.controller.AgentConfigController}的PUT /{agentId}/config接口使用。</p>
 *
 * <p>字段映射关系：
 * <ul>
 *   <li>context - 对应Agent实体的configJson字段，包含模型、提示词、工具等运行时配置</li>
 *   <li>shareConfig - 对应Agent实体的shareConfig字段，包含分享相关的配置（如分享链接、有效期等）</li>
 * </ul></p>
 *
 * <p>在AgentConfigController中，context会被映射为AgentUpdateDTO的configJson字段后传递给AgentService进行更新。</p>
 *
 * @see com.fastrag.module.agent.controller.AgentConfigController 使用此DTO的控制器
 * @see AgentUpdateDTO Agent通用更新DTO
 */
@Data
public class AgentConfigDTO {

    private Map<String, Object> context;

    private Map<String, Object> shareConfig;
}
