package com.fastrag.module.tools.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.*;
import com.fastrag.module.tools.mapper.*;
import com.fastrag.module.tools.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class McpServiceServiceImpl implements McpServiceService {

    private final McpServiceMapper mapper;
    private final McpToolMapper toolMapper;

    @Override
    public List<McpService> list(String keyword) {
        LambdaQueryWrapper<McpService> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) w.like(McpService::getName, keyword);
        w.orderByDesc(McpService::getCreatedAt);
        return mapper.selectList(w);
    }

    @Override
    public List<McpService> listEnabled() {
        return mapper.selectList(
                new LambdaQueryWrapper<McpService>()
                        .eq(McpService::getEnabled, 1)
                        .orderByDesc(McpService::getCreatedAt)
        );
    }

    @Override
    public List<McpService> listBuiltin() {
        return mapper.selectBuiltin();
    }

    @Override
    public McpService get(String id) {
        return mapper.selectById(id);
    }

    @Override
    public McpService getBySlug(String slug) {
        return mapper.selectBySlug(slug);
    }

    @Override
    public McpService create(Map<String, Object> form) {
        McpService s = new McpService();
        s.setName((String) form.get("name"));
        s.setSlug((String) form.get("slug"));
        s.setTransport((String) form.getOrDefault("transport", "sse"));
        s.setMcpUrl((String) form.get("mcpUrl"));
        s.setCommand((String) form.get("command"));

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) form.get("args");
        s.setArgs(args);

        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) form.get("env");
        s.setEnv(env);

        s.setAuthType((String) form.getOrDefault("authType", "none"));
        s.setAuthValue((String) form.get("authValue"));
        s.setEnabled(1);
        s.setStatus("offline");
        s.setCreatedAt(LocalDateTime.now());
        mapper.insert(s);
        return s;
    }

    @Override
    public McpService update(String id, Map<String, Object> form) {
        McpService s = mapper.selectById(id);
        if (s != null) {
            if (form.containsKey("name")) s.setName((String) form.get("name"));
            if (form.containsKey("slug")) s.setSlug((String) form.get("slug"));
            if (form.containsKey("mcpUrl")) s.setMcpUrl((String) form.get("mcpUrl"));
            if (form.containsKey("command")) s.setCommand((String) form.get("command"));
            if (form.containsKey("authType")) s.setAuthType((String) form.get("authType"));
            if (form.containsKey("authValue")) s.setAuthValue((String) form.get("authValue"));
            if (form.containsKey("env")) {
                @SuppressWarnings("unchecked")
                Map<String, String> env = (Map<String, String>) form.get("env");
                s.setEnv(env);
            }
            s.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(s);
        }
        return s;
    }

    @Override
    public void delete(String id) {
        McpService s = mapper.selectById(id);
        if (s != null && Integer.valueOf(1).equals(s.getIsBuiltin())) {
            throw new RuntimeException("内置 MCP 服务不允许删除");
        }
        mapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(String id) {
        McpService s = mapper.selectById(id);
        if (s != null) {
            s.setEnabled(s.getEnabled() == 1 ? 0 : 1);
            mapper.updateById(s);
        }
    }

    @Override
    public void toggleTool(Long toolId) {
        McpTool tool = toolMapper.selectById(toolId);
        if (tool != null) {
            tool.setEnabled(tool.getEnabled() == 1 ? 0 : 1);
            toolMapper.updateById(tool);
        }
    }

    @Override
    public List<McpTool> listTools(String serviceId) {
        return toolMapper.selectList(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId, serviceId)
        );
    }

    @Override
    public boolean existsBySlug(String slug) {
        return mapper.existsBySlug(slug);
    }
}
