package com.fastrag.module.agent.fs;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能只读文件系统后端。
 * 挂载点: /home/gem/skills/
 * 允许 Agent 读取技能目录中的 SKILL.md 文件。
 * 所有写操作被拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillReadonlyBackend implements VirtualFileSystem {

    private static final String MOUNT_POINT = "/home/gem/skills/";
    private static final Pattern SKILL_PATH_PATTERN = 
        Pattern.compile("^/home/gem/skills/([^/]+)(?:/(.*))?$");

    private final SkillService skillService;

    @Override
    public String getMountPoint() {
        return MOUNT_POINT;
    }

    @Override
    public boolean isAllowedWrite(String filePath) {
        return false; // 只读
    }

    @Override
    public List<FileEntry> ls(String path) {
        // 规范化路径
        String normalized = normalizePath(path);

        // 根目录: 列出所有已启用的技能
        if (MOUNT_POINT.equals(normalized) || "/home/gem/skills".equals(normalized)) {
            List<Skill> skills = skillService.listAccessible(null, null, null);
            List<FileEntry> entries = new ArrayList<>();
            for (Skill s : skills) {
                entries.add(new FileEntry(s.getSlug(), true));
            }
            return entries;
        }

        // 技能目录: 列出 SKILL.md 和其他已知文件
        Matcher m = SKILL_PATH_PATTERN.matcher(normalized);
        if (m.matches()) {
            String slug = m.group(1);
            String subPath = m.group(2);

            Skill skill = skillService.getBySlug(slug);
            if (skill == null) return List.of();

            if (subPath == null || subPath.isEmpty()) {
                // 技能根目录
                List<FileEntry> entries = new ArrayList<>();
                entries.add(new FileEntry("SKILL.md", false));
                return entries;
            }

            return List.of();
        }

        return List.of();
    }

    @Override
    public FileContent read(String filePath) {
        String normalized = normalizePath(filePath);
        log.debug("[SkillFS] Read: {}", normalized);

        Matcher m = SKILL_PATH_PATTERN.matcher(normalized);
        if (!m.matches()) {
            return FileContent.notFound(filePath, "无效的技能路径: " + filePath);
        }

        String slug = m.group(1);
        String fileName = m.group(2);

        Skill skill = skillService.getBySlug(slug);
        if (skill == null) {
            return FileContent.notFound(filePath, "技能不存在: " + slug);
        }

        // 只允许读取 SKILL.md
        if (fileName == null || fileName.isEmpty() || "SKILL.md".equals(fileName)) {
            String content = buildSkillMdContent(skill);
            return FileContent.ok(filePath, content);
        }

        return FileContent.notFound(filePath, "文件不可读: " + fileName + "（仅支持读取 SKILL.md）");
    }

    @Override
    public List<FileEntry> grep(String pattern, String path) {
        // 在技能文件内容中搜索
        String normalized = normalizePath(path);
        List<FileEntry> matches = new ArrayList<>();

        if (normalized.equals(MOUNT_POINT) || normalized.equals("/home/gem/skills")) {
            // 在所有技能中搜索
            List<Skill> skills = skillService.listAccessible(null, null, null);
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            for (Skill s : skills) {
                if (s.getContent() != null && p.matcher(s.getContent()).find()) {
                    matches.add(new FileEntry(s.getSlug() + "/SKILL.md", false));
                }
                if (s.getName() != null && p.matcher(s.getName()).find()) {
                    matches.add(new FileEntry(s.getSlug() + "/SKILL.md", false));
                }
                if (s.getDescription() != null && p.matcher(s.getDescription()).find()) {
                    matches.add(new FileEntry(s.getSlug() + "/SKILL.md", false));
                }
            }
        }
        return matches;
    }

    /**
     * 从数据库中的技能数据构建 SKILL.md 格式内容
     */
    private String buildSkillMdContent(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skill.getName() != null ? skill.getName() : "").append("\n");
        sb.append("slug: ").append(skill.getSlug() != null ? skill.getSlug() : "").append("\n");
        sb.append("description: ").append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
        if (skill.getVersion() != null) {
            sb.append("version: ").append(skill.getVersion()).append("\n");
        }
        if (skill.getCategory() != null) {
            sb.append("category: ").append(skill.getCategory()).append("\n");
        }
        if (skill.getAuthor() != null) {
            sb.append("author: ").append(skill.getAuthor()).append("\n");
        }
        sb.append("---\n\n");
        if (skill.getContent() != null) {
            sb.append(skill.getContent()).append("\n");
        } else {
            sb.append(skill.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 规范化路径：确保以 / 开头，去除尾部 /
     */
    private String normalizePath(String path) {
        if (path == null) return MOUNT_POINT;
        String normalized = path.replace("\\", "/").trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        // 去除尾部斜杠（根路径除外）
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
