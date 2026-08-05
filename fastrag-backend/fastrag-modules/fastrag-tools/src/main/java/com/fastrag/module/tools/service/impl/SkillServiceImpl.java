package com.fastrag.module.tools.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependency;
import com.fastrag.module.tools.entity.SkillImportResult;
import com.fastrag.module.tools.entity.SkillScope;
import com.fastrag.module.tools.mapper.SkillDependencyMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.mapper.SkillScopeMapper;
import com.fastrag.module.tools.service.SkillService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillMapper mapper;
    private final SkillDependencyMapper dependencyMapper;
    private final SkillScopeMapper scopeMapper;

    /** 技能可见性：系统级（内置/存量） / 属主 / 同组织 / API Token */
    private boolean visible(Skill s, LoginUser user) {
        return DataScope.visible(user, s.getCreator(), s.getOrgId(), s.getIsBuiltin());
    }

    private void requireManage(Skill s) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (s == null) throw BusinessException.notFound("技能不存在");
        if (!DataScope.manageable(user, s.getCreator())) throw BusinessException.forbidden("无权管理该技能");
    }

    /** 非 API Token 用户追加可见性条件（属主 / 同组织 / 系统级） */
    private void applyScope(LambdaQueryWrapper<Skill> w) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            w.and(q -> q.eq(Skill::getCreator, user.getUserId())
                    .or(o -> o.eq(Skill::getOrgId, user.getOrgId())
                            .or().eq(Skill::getCreator, "system")
                            .or().eq(Skill::getIsBuiltin, 1)));
        }
    }

    @Override
    public List<Skill> list(String keyword, String category) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            w.like(Skill::getName, keyword)
             .or(l -> l.like(Skill::getIdentifier, keyword))
             .or(l -> l.like(Skill::getDescription, keyword));
        }
        if (StrUtil.isNotBlank(category)) w.eq(Skill::getCategory, category);
        applyScope(w);
        w.orderByDesc(Skill::getUpdatedAt);
        return mapper.selectList(w);
    }

    @Override
    public List<Skill> listAccessible(String keyword, String category, String sourceType) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        w.eq(Skill::getEnabled, 1);
        if (StrUtil.isNotBlank(keyword)) {
            w.like(Skill::getName, keyword)
             .or(l -> l.like(Skill::getIdentifier, keyword))
             .or(l -> l.like(Skill::getDescription, keyword));
        }
        if (StrUtil.isNotBlank(category)) w.eq(Skill::getCategory, category);
        if (StrUtil.isNotBlank(sourceType)) w.eq(Skill::getSourceType, sourceType);
        applyScope(w);
        w.orderByDesc(Skill::getUpdatedAt);
        return mapper.selectList(w);
    }

    @Override
    public List<Skill> listBuiltin() {
        return mapper.selectBuiltin();
    }

    @Override
    public Skill get(String id) {
        Skill s = mapper.selectById(id);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (s == null || !visible(s, user)) throw BusinessException.forbidden("无权访问该技能");
        return s;
    }

    @Override
    public Skill getBySlug(String slug) {
        Skill s = mapper.selectBySlug(slug);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (s == null || !visible(s, user)) throw BusinessException.forbidden("无权访问该技能");
        return s;
    }

    @Override
    @Transactional
    public Skill create(Map<String, Object> form) {
        LoginUser user = SecurityUtil.getCurrentUser();
        Skill s = new Skill();
        s.setCreator(user.getUserId());
        s.setOrgId(user.getOrgId());
        applyBasicFields(s, form);
        s.setSourceType((String) form.getOrDefault("sourceType", "custom"));
        s.setIsBuiltin(0);
        s.setEnabled(1);
        s.setRecommended(0);
        s.setUsageCount(0);

        mapper.insert(s);

        // 写入依赖和范围
        saveDependencies(s.getId(), form);
        saveScopes(s.getId(), form);

        return s;
    }

    @Override
    @Transactional
    public Skill update(String id, Map<String, Object> form) {
        Skill s = mapper.selectById(id);
        requireManage(s);
        if (s == null) return null;

        applyBasicFields(s, form);

        if (form.containsKey("sourceType")) s.setSourceType((String) form.get("sourceType"));
        if (form.containsKey("enabled")) s.setEnabled((Integer) form.get("enabled"));
        if (form.containsKey("recommended")) s.setRecommended((Integer) form.get("recommended"));
        if (form.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) form.get("metadata");
            s.setMetadata(meta);
        }
        if (form.containsKey("shareConfig")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sc = (Map<String, Object>) form.get("shareConfig");
            s.setShareConfig(sc);
        }

        mapper.updateById(s);

        // 更新依赖和范围
        if (form.containsKey("dependencies")) {
            saveDependencies(s.getId(), form);
        }
        if (form.containsKey("scopes")) {
            saveScopes(s.getId(), form);
        }

        return s;
    }

    private void applyBasicFields(Skill s, Map<String, Object> form) {
        if (form.containsKey("name")) s.setName((String) form.get("name"));
        if (form.containsKey("identifier")) s.setIdentifier((String) form.get("identifier"));
        if (form.containsKey("slug")) s.setSlug((String) form.get("slug"));
        if (form.containsKey("description")) s.setDescription((String) form.get("description"));
        if (form.containsKey("icon")) s.setIcon((String) form.get("icon"));
        if (form.containsKey("source")) s.setSource((String) form.get("source"));
        if (form.containsKey("category")) s.setCategory((String) form.get("category"));
        if (form.containsKey("trigger")) s.setTrigger((String) form.get("trigger"));
        if (form.containsKey("content")) s.setContent((String) form.get("content"));
        if (form.containsKey("codeType")) s.setCodeType((String) form.get("codeType"));
        if (form.containsKey("code")) s.setCode((String) form.get("code"));
        if (form.containsKey("inputs")) s.setInputs((String) form.get("inputs"));
        if (form.containsKey("outputs")) s.setOutputs((String) form.get("outputs"));
        if (form.containsKey("author")) s.setAuthor((String) form.get("author"));
        if (form.containsKey("version")) s.setVersion((String) form.get("version"));
        if (form.containsKey("dirPath")) s.setDirPath((String) form.get("dirPath"));
        if (form.containsKey("contentHash")) s.setContentHash((String) form.get("contentHash"));
        if (form.containsKey("dependencies")) {
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) form.get("dependencies");
            s.setDependencies(deps);
        }
    }

    @SuppressWarnings("unchecked")
    private void saveDependencies(String skillId, Map<String, Object> form) {
        dependencyMapper.deleteBySkillId(skillId);

        Object raw = form.get("dependencies");
        if (raw == null) return;

        List<Map<String, Object>> depList;
        if (raw instanceof List) {
            depList = (List<Map<String, Object>>) raw;
        } else {
            return;
        }

        for (Map<String, Object> d : depList) {
            SkillDependency dep = new SkillDependency();
            dep.setSkillId(skillId);
            dep.setType((String) d.get("type"));
            dep.setName((String) d.get("name"));
            Object requiredVal = d.get("required");
            dep.setRequired(Boolean.TRUE.equals(requiredVal) || Integer.valueOf(1).equals(requiredVal) ? 1 : 0);
            dependencyMapper.insert(dep);
        }
    }

    @SuppressWarnings("unchecked")
    private void saveScopes(String skillId, Map<String, Object> form) {
        scopeMapper.deleteBySkillId(skillId);

        Object raw = form.get("scopes");
        if (raw == null) return;

        List<Map<String, Object>> scopeList;
        if (raw instanceof List) {
            scopeList = (List<Map<String, Object>>) raw;
        } else {
            return;
        }

        for (Map<String, Object> sc : scopeList) {
            SkillScope scope = new SkillScope();
            scope.setSkillId(skillId);
            scope.setScopeId((String) sc.get("id"));
            scope.setScopeName((String) sc.get("name"));
            Object enabledVal = sc.get("enabled");
            scope.setEnabled(Boolean.TRUE.equals(enabledVal) || Integer.valueOf(1).equals(enabledVal) ? 1 : 0);
            scopeMapper.insert(scope);
        }
    }

    @Override
    @Transactional
    public void delete(String id) {
        Skill s = mapper.selectById(id);
        requireManage(s);
        if (s != null && Integer.valueOf(1).equals(s.getIsBuiltin())) {
            throw new RuntimeException("内置技能不允许删除");
        }
        // 级联删除依赖和范围
        dependencyMapper.deleteBySkillId(id);
        scopeMapper.deleteBySkillId(id);
        mapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(String id) {
        Skill s = mapper.selectById(id);
        requireManage(s);
        if (s != null) {
            s.setEnabled(s.getEnabled() == 1 ? 0 : 1);
            mapper.updateById(s);
        }
    }

    @Override
    public void setEnabled(String id, boolean enabled) {
        Skill s = mapper.selectById(id);
        requireManage(s);
        if (s != null) {
            s.setEnabled(enabled ? 1 : 0);
            mapper.updateById(s);
        }
    }

    @Override
    public boolean existsBySlug(String slug) {
        return mapper.existsBySlug(slug);
    }

    @Override
    @Transactional
    public Skill updateDependencies(String id, List<Map<String, Object>> dependencies) {
        Map<String, Object> form = new HashMap<>();
        form.put("dependencies", dependencies);
        saveDependencies(id, form);
        // 同时更新 skill 表的 dependencies JSON 字段
        Skill s = mapper.selectById(id);
        if (s != null) {
            List<String> depRefs = dependencies.stream()
                .map(d -> d.get("type") + ":" + d.get("name"))
                .collect(Collectors.toList());
            s.setDependencies(depRefs);
            mapper.updateById(s);
        }
        return s;
    }

    @Override
    @Transactional
    public Skill updateShareConfig(String id, Map<String, Object> shareConfig) {
        Skill s = mapper.selectById(id);
        if (s != null) {
            s.setShareConfig(shareConfig);
            mapper.updateById(s);
        }
        return s;
    }

    @Override
    public List<Map<String, Object>> getDependencies(String id) {
        List<SkillDependency> deps = dependencyMapper.selectList(
            new LambdaQueryWrapper<SkillDependency>().eq(SkillDependency::getSkillId, id));
        return deps.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("type", d.getType());
            m.put("name", d.getName());
            m.put("required", d.getRequired());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getScopes(String id) {
        List<SkillScope> scopes = scopeMapper.selectList(
            new LambdaQueryWrapper<SkillScope>().eq(SkillScope::getSkillId, id));
        return scopes.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getScopeId());
            m.put("name", s.getScopeName());
            m.put("enabled", s.getEnabled());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SkillImportResult importSkillDir(Path sourceDir, String operator) {
        try {
            // 查找 SKILL.md
            Path skillMd = sourceDir.resolve("SKILL.md");
            if (!Files.exists(skillMd)) {
                return SkillImportResult.fail("目录中未找到 SKILL.md");
            }

            String content = Files.readString(skillMd);

            // 使用 SkillMarkdownParser 解析
            // 注意：这里不能直接注入 parser，因为 importSkillDir 在 SkillService 中
            // 实际调用方（如 SkillDataInitializer）会在初始化时直接处理
            // 此处留作接口扩展
            return SkillImportResult.fail("请使用 SkillDataInitializer 直接处理");

        } catch (Exception e) {
            log.error("[SkillService] 导入技能失败: {}", sourceDir, e);
            return SkillImportResult.fail(e.getMessage());
        }
    }
}
