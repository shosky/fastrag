package com.fastrag.module.tools.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.tools.entity.Tool;
import com.fastrag.module.tools.entity.ToolHttpConfig;
import com.fastrag.module.tools.mapper.ToolMapper;
import com.fastrag.module.tools.mapper.ToolHttpConfigMapper;
import com.fastrag.module.tools.service.ToolService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {
    private final ToolMapper mapper;
    private final ToolHttpConfigMapper httpMapper;

    /** 工具可见性：系统级（内置/存量） / 属主 / 同组织 / API Token */
    private boolean visible(Tool t, LoginUser user) {
        return DataScope.visible(user, t.getCreator(), t.getOrgId(), t.getIsBuiltin());
    }

    private void requireManage(Tool t) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (t == null) throw BusinessException.notFound("工具不存在");
        if (!DataScope.manageable(user, t.getCreator())) throw BusinessException.forbidden("无权管理该工具");
    }

    @Override
    public List<Tool> list(String kw, String type) {
        var w = new LambdaQueryWrapper<Tool>();
        if (StrUtil.isNotBlank(kw)) w.like(Tool::getName, kw);
        if (StrUtil.isNotBlank(type)) w.eq(Tool::getType, type);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            w.and(q -> q.eq(Tool::getCreator, user.getUserId())
                    .or(o -> o.eq(Tool::getOrgId, user.getOrgId())
                            .or().eq(Tool::getCreator, "system")
                            .or().eq(Tool::getIsBuiltin, 1)));
        }
        List<Tool> list = mapper.selectList(w);
        list.forEach(this::attachHttpConfig);
        // 非属主不返回 HTTP 配置（含 URL/authValue 凭据）
        list.forEach(t -> { if (t.getHttpConfig() != null && !DataScope.manageable(user, t.getCreator())) t.setHttpConfig(null); });
        return list;
    }

    @Override
    public Tool get(String id) {
        Tool t = mapper.selectById(id);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (t == null || !visible(t, user)) throw BusinessException.forbidden("无权访问该工具");
        attachHttpConfig(t);
        // 非属主不返回 HTTP 配置（含 URL/authValue 凭据）
        if (t.getHttpConfig() != null && !DataScope.manageable(user, t.getCreator())) t.setHttpConfig(null);
        return t;
    }

    /** 从 tool_http_config 表加载 HTTP 配置并附加到 Tool 对象 */
    private void attachHttpConfig(Tool t) {
        ToolHttpConfig cfg = httpMapper.selectById(t.getId());
        if (cfg != null) {
            // 解析 params 和 headers JSON 字符串为对象
            t.setHttpConfig(cfg);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Tool create(Map<String, Object> f) {
        LoginUser user = SecurityUtil.getCurrentUser();
        var t = new Tool();
        t.setName((String) f.get("name"));
        t.setIdentifier((String) f.get("identifier"));
        t.setDescription((String) f.get("description"));
        t.setType((String) f.getOrDefault("type", "http"));
        t.setCreator(user.getUserId());
        t.setOrgId(user.getOrgId());
        t.setIsBuiltin(0);
        // enabled: 前端发 Boolean，转 Integer(1/0)
        if (f.containsKey("enabled")) {
            Object en = f.get("enabled");
            if (en instanceof Boolean) t.setEnabled(((Boolean) en) ? 1 : 0);
            else if (en instanceof Number) t.setEnabled(((Number) en).intValue());
            else t.setEnabled(1);
        } else {
            t.setEnabled(1);
        }

        // 输入参数 JSON Schema
        if (f.containsKey("inputs") && f.get("inputs") instanceof Map) {
            t.setInputs((Map<String, Object>) f.get("inputs"));
        } else if (f.containsKey("inputSchema") && f.get("inputSchema") instanceof Map) {
            t.setInputs((Map<String, Object>) f.get("inputSchema"));
        }

        // 输出参数 JSON Schema
        if (f.containsKey("outputs") && f.get("outputs") instanceof Map) {
            t.setOutputs((Map<String, Object>) f.get("outputs"));
        } else if (f.containsKey("outputSchema") && f.get("outputSchema") instanceof Map) {
            t.setOutputs((Map<String, Object>) f.get("outputSchema"));
        }

        // 输出映射
        if (f.containsKey("outputMapping")) {
            t.setOutputMapping(f.get("outputMapping") != null ? f.get("outputMapping").toString() : null);
        }

        // 标签和图标
        if (f.containsKey("tags")) {
            t.setTags(f.get("tags"));
        }
        if (f.containsKey("icon")) {
            t.setIcon((String) f.get("icon"));
        }

        // 自动生成 identifier
        if (StrUtil.isBlank(t.getIdentifier()) && StrUtil.isNotBlank(t.getName())) {
            t.setIdentifier(t.getName().toLowerCase().replaceAll("\\s+", "_"));
        }

        mapper.insert(t);

        // 保存 HTTP 配置（如果有）
        saveHttpConfigFromForm(t.getId(), f);

        return t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Tool update(String id, Map<String, Object> f) {
        var t = mapper.selectById(id);
        requireManage(t);
        if (t == null) return null;

        if (f.containsKey("name")) t.setName((String) f.get("name"));
        if (f.containsKey("description")) t.setDescription((String) f.get("description"));
        if (f.containsKey("identifier")) t.setIdentifier((String) f.get("identifier"));
        if (f.containsKey("type")) t.setType((String) f.get("type"));
        if (f.containsKey("enabled")) {
            Object en = f.get("enabled");
            if (en instanceof Boolean) t.setEnabled(((Boolean) en) ? 1 : 0);
            else if (en instanceof Number) t.setEnabled(((Number) en).intValue());
        }

        // 输入参数 JSON Schema
        if (f.containsKey("inputs") && f.get("inputs") instanceof Map) {
            t.setInputs((Map<String, Object>) f.get("inputs"));
        } else if (f.containsKey("inputSchema") && f.get("inputSchema") instanceof Map) {
            t.setInputs((Map<String, Object>) f.get("inputSchema"));
        }

        // 输出参数 JSON Schema
        if (f.containsKey("outputs") && f.get("outputs") instanceof Map) {
            t.setOutputs((Map<String, Object>) f.get("outputs"));
        } else if (f.containsKey("outputSchema") && f.get("outputSchema") instanceof Map) {
            t.setOutputs((Map<String, Object>) f.get("outputSchema"));
        }

        // 输出映射
        if (f.containsKey("outputMapping")) {
            t.setOutputMapping(f.get("outputMapping") != null ? f.get("outputMapping").toString() : null);
        }

        // 标签和图标
        if (f.containsKey("tags")) t.setTags(f.get("tags"));
        if (f.containsKey("icon")) t.setIcon((String) f.get("icon"));

        mapper.updateById(t);

        // 更新 HTTP 配置（如果有）
        saveHttpConfigFromForm(id, f);

        return t;
    }

    @Override
    public void delete(String id) {
        var t = mapper.selectById(id);
        requireManage(t);
        mapper.deleteById(id);
        httpMapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(String id) {
        var t = mapper.selectById(id);
        requireManage(t);
        if (t != null) {
            t.setEnabled(t.getEnabled() == 1 ? 0 : 1);
            mapper.updateById(t);
        }
    }

    @Override
    public ToolHttpConfig getApiConfig(String toolId) {
        var t = mapper.selectById(toolId);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (t == null || !visible(t, user)) throw BusinessException.forbidden("无权访问该工具");
        // HTTP 配置（URL/authValue）仅属主可见
        if (!DataScope.manageable(user, t.getCreator())) throw BusinessException.forbidden("无权查看该工具配置");
        var cfg = httpMapper.selectById(toolId);
        if (cfg == null) {
            cfg = new ToolHttpConfig();
            cfg.setToolId(toolId);
            cfg.setMethod("GET");
        }
        return cfg;
    }

    @Override
    public ToolHttpConfig saveApiConfig(String toolId, ToolHttpConfig config) {
        var t = mapper.selectById(toolId);
        requireManage(t);
        config.setToolId(toolId);
        var existing = httpMapper.selectById(toolId);
        if (existing == null) httpMapper.insert(config);
        else httpMapper.updateById(config);
        return config;
    }

    /** 从前端提交的 form map 中提取 httpConfig 子对象并保存 */
    @SuppressWarnings("unchecked")
    private void saveHttpConfigFromForm(String toolId, Map<String, Object> f) {
        Object httpConfigObj = f.get("httpConfig");
        if (!(httpConfigObj instanceof Map)) return;
        Map<String, Object> httpConfig = (Map<String, Object>) httpConfigObj;

        var cfg = new ToolHttpConfig();
        cfg.setToolId(toolId);
        cfg.setMethod(getString(httpConfig, "method", "GET"));
        cfg.setUrl(getString(httpConfig, "url", ""));
        cfg.setAuthType(getString(httpConfig, "authType", "none"));
        cfg.setBodyType(getString(httpConfig, "bodyType", "none"));
        cfg.setBody(getString(httpConfig, "body", ""));
        cfg.setAuthValue(getString(httpConfig, "authValue", ""));

        // params 和 headers: 前端发 [{key, value}, ...] 数组
        cfg.setParams(toJsonString(httpConfig.get("params")));
        cfg.setHeaders(toJsonString(httpConfig.get("headers")));

        var existing = httpMapper.selectById(toolId);
        if (existing == null) httpMapper.insert(cfg);
        else httpMapper.updateById(cfg);
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    // ===== 插件管理扩展 =====

    @Override
    public Tool uploadPlugin(MultipartFile file, String name, String description) {
        var t = new Tool();
        t.setName(name != null ? name : file.getOriginalFilename());
        t.setIdentifier(UUID.randomUUID().toString().substring(0, 8));
        t.setDescription(description != null ? description : "通过上传创建的插件");
        t.setType("plugin");
        t.setEnabled(1);
        mapper.insert(t);
        return t;
    }

    @Override
    public List<Tool> importFromJson(List<Map<String, Object>> plugins) {
        List<Tool> result = new ArrayList<>();
        for (var f : plugins) {
            var t = new Tool();
            t.setName((String) f.get("name"));
            t.setIdentifier((String) f.getOrDefault("identifier", UUID.randomUUID().toString().substring(0, 8)));
            t.setDescription((String) f.get("description"));
            t.setType((String) f.getOrDefault("type", "http"));
            t.setEnabled(1);
            mapper.insert(t);
            result.add(t);
        }
        return result;
    }
}
