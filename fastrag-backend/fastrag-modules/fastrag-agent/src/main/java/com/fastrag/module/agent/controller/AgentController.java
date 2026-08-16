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
 * Agent管理REST控制器，提供Agent的完整CRUD操作以及默认Agent设置接口。
 *
 * <p>提供的REST API端点：
 * <ul>
 *   <li>GET / - 列出当前用户可见的所有Agent（支持includeSubagents参数过滤子Agent）</li>
 *   <li>GET /default - 获取系统默认Agent</li>
 *   <li>POST / - 创建新Agent</li>
 *   <li>GET /{agentId} - 获取单个Agent详情（包含完整的后端信息）</li>
 *   <li>PUT /{agentId} - 更新Agent信息</li>
 *   <li>DELETE /{agentId} - 删除Agent</li>
 *   <li>POST /{agentId}/set_default - 设置指定Agent为默认Agent</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>所有接口通过@CurrentUser注解获取当前用户，进行可见性和权限校验</li>
 *   <li>写操作（更新、删除、设置默认）需要管理权限校验（userCanManage），无权返回403</li>
 *   <li>返回值统一使用{@link AgentSerializeVO}序列化，将Agent实体转换为前端友好的VO对象</li>
 *   <li>agentId参数同时支持slug和ID两种方式查找</li>
 *   <li>依赖AgentBackendManager获取后端信息，在序列化时填充后端元数据</li>
 *   <li>使用@Loggable注解记录操作日志</li>
 * </ul></p>
 *
 * @see AgentService Agent业务服务
 * @see AgentCreateDTO 创建请求DTO
 * @see AgentUpdateDTO 更新请求DTO
 * @see AgentSerializeVO 序列化输出VO
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
