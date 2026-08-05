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
 * REST controller for agent configuration operations.
 * Provides endpoints to retrieve and update agent config.
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
