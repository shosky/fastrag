package com.fastrag.module.agent.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.agent.backend.AgentBackendManager;
import com.fastrag.module.agent.context.CurrentUser;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentCreateDTO;
import com.fastrag.module.agent.dto.AgentSerializeVO;
import com.fastrag.module.agent.dto.AgentUpdateDTO;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

/**
 * REST controller for agent CRUD operations.
 * Provides endpoints to list, create, read, update, delete agents,
 * as well as set the default agent.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentBackendManager agentBackendManager;

    /**
     * List all agents visible to the current user.
     *
     * @param includeSubagents whether to include sub-agents in the result
     * @param user             the current authenticated user
     * @return list of serialized agent VOs
     */
    @GetMapping
    public ApiResponse<List<AgentSerializeVO>> listAgents(
            @RequestParam(defaultValue = "false") boolean includeSubagents,
            @CurrentUser User user) {
        List<Agent> agents = agentService.listVisible(user, includeSubagents);
        List<AgentSerializeVO> voList = agents.stream()
                .map(a -> agentService.serialize(a, user, false))
                .collect(Collectors.toList());
        return ApiResponse.success(voList);
    }

    /**
     * Get the default agent.
     *
     * @param user the current authenticated user
     * @return the default agent serialized VO
     */
    @GetMapping("/default")
    public ApiResponse<AgentSerializeVO> getDefaultAgent(@CurrentUser User user) {
        Agent agent = agentService.ensureDefaultAgent();
        AgentSerializeVO vo = agentService.serialize(agent, user, false);
        return ApiResponse.success(vo);
    }

    /**
     * Create a new agent.
     *
     * @param dto  the agent creation DTO
     * @param user the current authenticated user
     * @return the created agent serialized VO
     */
    @PostMapping
    public ApiResponse<AgentSerializeVO> createAgent(
            @RequestBody @jakarta.validation.Valid AgentCreateDTO dto,
            @CurrentUser User user) {
        Agent agent = agentService.create(dto, user);
        AgentSerializeVO vo = agentService.serialize(agent, user, true);
        return ApiResponse.success(vo);
    }

    /**
     * Get a single agent by its slug.
     *
     * @param agentId the agent slug or id
     * @param user    the current authenticated user
     * @return the agent serialized VO with full backend info
     */
    @GetMapping("/{agentId}")
    public ApiResponse<AgentSerializeVO> getAgent(
            @PathVariable String agentId,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, true);
        AgentSerializeVO vo = agentService.serialize(agent, user, true);
        return ApiResponse.success(vo);
    }

    /**
     * Update an existing agent.
     *
     * @param agentId the agent slug or id
     * @param dto     the update DTO with fields to change
     * @param user    the current authenticated user
     * @return the updated agent serialized VO
     */
    @PutMapping("/{agentId}")
    public ApiResponse<AgentSerializeVO> updateAgent(
            @PathVariable String agentId,
            @RequestBody @jakarta.validation.Valid AgentUpdateDTO dto,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, false);
        if (!agentService.userCanManage(user, agent)) {
            return ApiResponse.forbidden("无权管理该Agent");
        }
        Agent updated = agentService.update(agent, dto, user);
        AgentSerializeVO vo = agentService.serialize(updated, user, true);
        return ApiResponse.success(vo);
    }

    /**
     * Delete an agent by its slug.
     *
     * @param agentId the agent slug or id
     * @param user    the current authenticated user
     * @return success response
     */
    @DeleteMapping("/{agentId}")
    public ApiResponse<Void> deleteAgent(
            @PathVariable String agentId,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, false);
        if (!agentService.userCanManage(user, agent)) {
            return ApiResponse.forbidden("无权管理该Agent");
        }
        agentService.delete(agent);
        return ApiResponse.success();
    }

    /**
     * Set an agent as the default agent.
     *
     * @param agentId the agent slug or id
     * @param user    the current authenticated user
     * @return the newly-default agent serialized VO
     */
    @PostMapping("/{agentId}/set_default")
    public ApiResponse<AgentSerializeVO> setDefaultAgent(
            @PathVariable String agentId,
            @CurrentUser User user) {
        Agent agent = agentService.getVisibleBySlug(agentId, user, false);
        if (!agentService.userCanManage(user, agent)) {
            return ApiResponse.forbidden("无权管理该Agent");
        }
        Agent updated = agentService.setDefault(agent);
        AgentSerializeVO vo = agentService.serialize(updated, user, false);
        return ApiResponse.success(vo);
    }
}
