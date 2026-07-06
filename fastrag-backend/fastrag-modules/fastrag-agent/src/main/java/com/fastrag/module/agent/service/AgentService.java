package com.fastrag.module.agent.service;

import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentCreateDTO;
import com.fastrag.module.agent.dto.AgentSerializeVO;
import com.fastrag.module.agent.dto.AgentUpdateDTO;
import com.fastrag.module.agent.entity.Agent;

import java.util.List;
import java.util.Map;

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
