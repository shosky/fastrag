package com.fastrag.module.agent.service;

import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentCreateDTO;
import com.fastrag.module.agent.dto.AgentSerializeVO;
import com.fastrag.module.agent.dto.AgentUpdateDTO;
import com.fastrag.module.agent.entity.Agent;

import java.util.List;
import java.util.Map;

/**
 * AI Agent核心服务接口。
 *
 * <p>定义系统中AI智能体（Agent）的完整生命周期管理，包括Agent的CRUD、
 * 可见性控制、权限检查、序列化和子Agent管理。
 * 是Agent模块的核心服务，被多个Controller和引擎组件使用。</p>
 *
 * <p>核心功能分组：</p>
 * <ul>
 *   <li>查询：listVisible（用户可见Agent列表）、getVisibleBySlug（按slug查询可见Agent）、
 *       getBySlug（按slug查询）、listVisibleSubagents/listVisibleSubagentBySlug（子Agent）</li>
 *   <li>写入：create（创建Agent）、update（更新Agent）、delete（删除Agent）、setDefault（设为默认Agent）</li>
 *   <li>权限：userCanAccess（检查用户是否可访问）、userCanManage（检查用户是否可管理）、
 *       isBuiltin（判断是否内置Agent）</li>
 *   <li>序列化：serialize（将Agent转为可序列化的VO对象）</li>
 *   <li>内置：ensureBuiltinAgent（确保内置Agent存在）、ensureUniqueSlug（生成唯一slug）、
 *       normalizeShareConfig（规范化分享配置）</li>
 * </ul>
 */
public interface AgentService {

    Agent ensureDefaultAgent();
    List<Agent> listVisible(User user, boolean includeSubagents);
    Agent getVisibleBySlug(String slug, User user, boolean withBackend);
    Agent getBySlug(String slug);
    Agent create(AgentCreateDTO dto, User user);
    Agent update(Agent agent, AgentUpdateDTO dto, User user);
    void delete(Agent agent);
    boolean userCanAccess(User user, Agent agent);
    boolean userCanManage(User user, Agent agent);
    boolean isBuiltin(Agent agent);
    Agent setDefault(Agent agent);
    AgentSerializeVO serialize(Agent agent, User user, boolean full);
    void ensureBuiltinAgent(String slug, String backendId, String name, String description,
                            String accessLevel, boolean isDefault, boolean isSubagent,
                            Map<String, Object> config);
    String ensureUniqueSlug(String baseSlug);
    Map<String, Object> normalizeShareConfig(Map<String, Object> shareConfig, User user);
    List<Agent> listVisibleSubagents(User user);
    Agent getVisibleSubagentBySlug(String slug, User user);
}
