package com.fastrag.module.tools.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        String slug = getString(meta, "slug", name);
        String description = getString(meta, "description", "");
        String version = getString(meta, "version", "1.0.0");
        String category = getString(meta, "category", "");
        String icon = getString(meta, "icon", "");

        if (name.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 name 字段");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("SKILL.md frontmatter 缺少 description 字段");
        }

        return new SkillParseResult(slug, name, description, version, category, icon, meta, fm.getBody());
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString().strip() : defaultValue;
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
    }
}
