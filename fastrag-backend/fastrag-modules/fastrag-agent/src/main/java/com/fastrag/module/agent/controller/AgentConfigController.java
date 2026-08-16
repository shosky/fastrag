package com.fastrag.module.agent.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.agent.context.CurrentUser;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentConfigDTO;
import com.fastrag.module.agent.dto.AgentUpdateDTO;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

/**
 * Agent配置管理REST控制器，提供Agent配置的查询和更新接口。
 *
 * <p>提供的REST API端点：
 * <ul>
 *   <li>GET /{agentId}/config - 获取指定Agent的配置信息（configJson和可配置项）</li>
 *   <li>PUT /{agentId}/config - 更新指定Agent的配置（上下文配置和分享配置）</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>通过agentId（slug或ID）查找Agent，使用@CurrentUser注解获取当前用户并校验可见性</li>
 *   <li>配置更新时校验用户是否有管理权限（userCanManage），无权则返回403</li>
 *   <li>PUT接口接收{@link AgentConfigDTO}，将其中的context映射为AgentUpdateDTO的configJson</li>
 *   <li>使用@Loggable注解记录操作日志（通过fastrag-common模块的日志框架）</li>
 * </ul></p>
 *
 * @see AgentService Agent业务服务
 * @see AgentConfigDTO 配置更新请求DTO
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentService agentService;

    /**
     * Get the configuration for an agent, including configurable items
     * derived from the agent's backend context schema.
     *
     * @param agentId the agent slug or id
     * @param user    the current authenticated user
     * @return a map containing configJson and configurable items
     */
    @GetMapping("/{agentId}/config")
    public ApiResponse<Map<String, Object>> getAgentConfig(
            @PathVariable String agentId,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, false);

        Map<String, Object> result = new HashMap<>();
        result.put("configJson", agent.getConfigJson());
        // Configurable items can be derived from the backend context schema
        // when AgentBackendManager is fully wired into AgentService
        result.put("configurableItems", new HashMap<>());

        return ApiResponse.success(result);
    }

    /**
     * Update the configuration for an agent.
     *
     * @param agentId the agent slug or id
     * @param dto     the configuration DTO containing context and shareConfig updates
     * @param user    the current authenticated user
     * @return success response
     */
    @PutMapping("/{agentId}/config")
    public ApiResponse<Void> updateAgentConfig(
            @PathVariable String agentId,
            @RequestBody @jakarta.validation.Valid AgentConfigDTO dto,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, false);
        if (!agentService.userCanManage(user, agent)) {
            return ApiResponse.forbidden("无权管理该Agent");
        }

        AgentUpdateDTO updateDTO = new AgentUpdateDTO();
        updateDTO.setConfigJson(dto.getContext());
        updateDTO.setShareConfig(dto.getShareConfig());

        agentService.update(agent, updateDTO, user);
        return ApiResponse.success();
    }
}
