package com.fastrag.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Agent创建请求DTO，用于创建新Agent时的请求参数。
 *
 * <p>使用场景：由{@link com.fastrag.module.agent.controller.AgentController}的POST /接口使用。</p>
 *
 * <p>字段映射关系（与{@link com.fastrag.module.agent.entity.Agent}实体的映射）：
 * <ul>
 *   <li>backendId - Agent后端类型标识（必填，对应Agent.backendId，如"ChatbotAgent"）</li>
 *   <li>name - Agent名称（必填，对应Agent.name）</li>
 *   <li>description - Agent描述（可选，对应Agent.description）</li>
 *   <li>slug - Agent唯一标识符（可选，对应Agent.slug，用于URL友好标识）</li>
 *   <li>icon - Agent图标（可选，对应Agent.icon）</li>
 *   <li>configJson - Agent配置JSON（可选，对应Agent.configJson，包含模型、工具等配置）</li>
 *   <li>shareConfig - 分享配置（可选，对应Agent.shareConfig）</li>
 * </ul></p>
 *
 * <p>校验规则：backendId和name字段通过@NotBlank确保不为空。</p>
 *
 * @see com.fastrag.module.agent.controller.AgentController 使用此DTO的控制器
 * @see com.fastrag.module.agent.entity.Agent 对应的实体类
 */
@Data
public class AgentCreateDTO {

    @NotBlank(message = "后端ID不能为空")
    private String backendId;

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    private String slug;

    private String icon;

    private Map<String, Object> configJson;

    private Map<String, Object> shareConfig;
}
