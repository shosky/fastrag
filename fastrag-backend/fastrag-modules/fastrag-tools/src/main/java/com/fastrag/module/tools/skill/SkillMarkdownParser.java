package com.fastrag.module.tools.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SKILL.md 解析器。
 * SKILL.md 文件格式为 YAML frontmatter（--- 分隔） + Markdown 正文。
 */
@Slf4j
@Component
public class SkillMarkdownParser {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 分割 frontmatter（--- 分隔的 YAML 头部）
     */
    public FrontmatterResult splitFrontmatter(String content) {
        // 去除 BOM 和前导换行
        content = content.replace("\uFEFF", "").stripLeading();

        if (!content.startsWith("---")) {
            throw new IllegalArgumentException("SKILL.md 必须以 --- frontmatter 开头");
        }

        // 查找第二个 ---
        int firstEnd = content.indexOf("---", 3);
        if (firstEnd == -1) {
            throw new IllegalArgumentException("frontmatter 格式错误：缺少闭合 ---");
        }

        String frontmatterRaw = content.substring(3, firstEnd).strip();
        String body = content.substring(firstEnd + 3).strip();

        return new FrontmatterResult(frontmatterRaw, body);
    }

    /**
     * 解析 SKILL.md 内容
     */
    public SkillParseResult parse(String content) {
        FrontmatterResult fm = splitFrontmatter(content);

        Map<String, Object> meta;
        try {
            meta = yamlMapper.readValue(fm.getFrontmatterRaw(), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML frontmatter 解析失败: " + e.getMessage(), e);
        }

        String name = getString(meta, "name", "");
        String slug = getString(meta, "slug", "");
        String description = getString(meta, "description", "");
        String version = getString(meta, "version", "1.0.0");
        String category = getString(meta, "category", "");
        String icon = getString(meta, "icon", "");

        // M3-2（D9）：frontmatter 依赖三字段解析（格式以 builtin-skills/web-search/SKILL.md 实际写法为准，
        // 兼容 string 与 list 两种形态；缺省为空列表）
        List<String> toolDeps = stringListValue(meta.get("tool_dependencies"));
        List<String> mcpDeps = stringListValue(meta.get("mcp_dependencies"));
        List<String> skillDeps = stringListValue(meta.get("skill_dependencies"));

        if (name.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 name 字段");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 description 字段");
        }

        // slug 白名单校验：缺省时由 name 派生，非法按 SKILL.md 无效语义抛出（消息含字段名）
        if (slug.isEmpty()) {
            slug = SlugValidator.normalize(name);
            if (slug == null) {
                throw new IllegalArgumentException(
                    "SKILL.md frontmatter 缺少合法 slug 字段，且无法由 name 派生（规则：小写字母或数字开头，仅含 a-z、0-9、.、_、-，长度 1-64）");
            }
        } else if (!SlugValidator.isValid(slug)) {
            throw new IllegalArgumentException(
                "SKILL.md frontmatter slug 字段非法: \"" + slug + "\"（规则：小写字母或数字开头，仅含 a-z、0-9、.、_、-，长度 1-64）");
        }

        return new SkillParseResult(slug, name, description, version, category, icon, meta, fm.getBody(),
                toolDeps, mcpDeps, skillDeps);
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString().strip() : defaultValue;
    }

    /** 依赖字段取值：list 形态逐项取字符串，string 形态归一为单元素列表；缺省/空为空列表 */
    private List<String> stringListValue(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(o -> o.toString().strip())
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String s = v.toString().strip();
        return s.isEmpty() ? List.of() : List.of(s);
    }

    /**
     * 解析结果转依赖串规范形态 {@code type:name}（tool → mcp → skill 顺序），
     * 与 {@code Skill.dependencies} JSON 及 SkillsMiddleware 闭包展开（skill: 前缀）约定一致。
     */
    public static List<String> dependencyRefs(SkillParseResult r) {
        if (r == null) return List.of();
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (String t : r.getToolDependencies()) refs.add("tool:" + t);
        for (String m : r.getMcpDependencies()) refs.add("mcp:" + m);
        for (String s : r.getSkillDependencies()) refs.add("skill:" + s);
        return List.copyOf(refs);
    }

    @Data
    @AllArgsConstructor
    public static class FrontmatterResult {
        private String frontmatterRaw;
        private String body;
    }

    @Data
    @AllArgsConstructor
    public static class SkillParseResult {
        private String slug;
        private String name;
        private String description;
        private String version;
        private String category;
        private String icon;
        private Map<String, Object> metadata;
        private String body;
        /** M3-2（D9）：frontmatter tool_dependencies（缺省空列表） */
        private List<String> toolDependencies;
        /** M3-2（D9）：frontmatter mcp_dependencies（缺省空列表） */
        private List<String> mcpDependencies;
        /** M3-2（D9）：frontmatter skill_dependencies（缺省空列表） */
        private List<String> skillDependencies;
    }
}
