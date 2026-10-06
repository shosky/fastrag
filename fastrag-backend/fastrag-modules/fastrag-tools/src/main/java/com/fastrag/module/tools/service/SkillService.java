package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillImportResult;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SkillService {
    List<Skill> list(String keyword, String category);
    List<Skill> listAccessible(String keyword, String category, String sourceType);

    /**
     * 按主键批量取技能实体（不做可见性/启用过滤——调用方与 listAccessible 求交集后自行裁决）。
     * M2-2：app/agent 绑定存的是 skill 主键 id，运行时装配经此解析。
     */
    List<Skill> listByIds(Collection<String> ids);
    List<Skill> listBuiltin();
    Skill get(String id);
    Skill getBySlug(String slug);
    Skill create(Map<String, Object> form);
    Skill update(String id, Map<String, Object> form);
    void delete(String id);
    void toggleEnabled(String id);
    boolean existsBySlug(String slug);

    /**
     * 更新技能依赖
     */
    Skill updateDependencies(String id, List<Map<String, Object>> dependencies);

    /**
     * 更新技能分享配置
     */
    Skill updateShareConfig(String id, Map<String, Object> shareConfig);

    /**
     * 设置技能启用状态
     */
    void setEnabled(String id, boolean enabled);

    /**
     * 从文件系统目录导入技能
     */
    SkillImportResult importSkillDir(Path sourceDir, String operator);

    /**
     * 获取技能依赖列表
     */
    List<Map<String, Object>> getDependencies(String id);

    /**
     * 获取技能作用域列表
     */
    List<Map<String, Object>> getScopes(String id);
}
