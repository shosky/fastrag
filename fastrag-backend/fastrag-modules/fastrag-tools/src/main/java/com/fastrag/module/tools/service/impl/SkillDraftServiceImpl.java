package com.fastrag.module.tools.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillInstallDraft;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.SkillDraftService;
import com.fastrag.module.tools.skill.SkillMarkdownParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillDraftServiceImpl implements SkillDraftService {

    private final SkillMapper skillMapper;
    private final SkillMarkdownParser markdownParser;
    private final ObjectMapper objectMapper;

    @Value("${fastrag.data-dir:${user.home}/.fastrag/data}")
    private String dataDir;

    private static final int DRAFT_TTL_HOURS = 1;
    private static final String DRAFTS_DIR = "skill_import_drafts";
    private static final String SKILLS_DIR = "skills";

    private Path getDraftsRoot() {
        return Paths.get(dataDir, DRAFTS_DIR);
    }

    private Path getSkillsRoot() {
        return Paths.get(dataDir, SKILLS_DIR);
    }

    @Override
    public SkillInstallDraft prepareUpload(String filename, byte[] fileBytes, String operator) {
        cleanupExpiredDrafts();

        String draftId = UUID.randomUUID().toString();
        Path draftDir = getDraftsRoot().resolve(draftId);

        try {
            Files.createDirectories(draftDir);

            String sourceType;
            if (filename.endsWith(".zip")) {
                sourceType = "upload";
                extractZip(fileBytes, draftDir);
            } else if (filename.endsWith(".md") || filename.endsWith(".markdown") || filename.equals("SKILL.md")) {
                sourceType = "upload";
                // 单文件上传：在草稿中创建以文件名命名的目录
                String slug = filename.replace(".md", "").replace(".markdown", "");
                Path skillDir = draftDir.resolve(slug);
                Files.createDirectories(skillDir);
                Files.write(skillDir.resolve("SKILL.md"), fileBytes);
            } else {
                throw new IllegalArgumentException("不支持的文件格式，请上传 .zip 或 .md 文件: " + filename);
            }

            // 如果 ZIP 根目录直接有 SKILL.md（单技能 ZIP），整理到 {slug}/ 子目录
            reorganizeSingleSkill(draftDir);

            // 扫描所有 SKILL.md
            List<SkillInstallDraft.DraftItem> items = scanSkillItems(draftDir);

            if (items.isEmpty()) {
                throw new IllegalArgumentException("未找到 SKILL.md 文件");
            }

            SkillInstallDraft draft = new SkillInstallDraft();
            draft.setDraftId(draftId);
            draft.setSourceType(sourceType);
            draft.setSource(filename);
            draft.setItems(items);
            draft.setExpiresAt(LocalDateTime.now().plusHours(DRAFT_TTL_HOURS));
            draft.setCreatedBy(operator);

            // 保存元数据
            Files.writeString(draftDir.resolve("metadata.json"),
                objectMapper.writeValueAsString(draft));

            log.info("[SkillDraft] 创建安装草稿: draftId={}, items={}", draftId, items.size());
            return draft;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("创建安装草稿失败", e);
        }
    }

    @Override
    @Transactional
    public List<SkillInstallDraft.DraftItem> confirmDraft(String draftId,
                                                           Map<String, Object> shareConfig,
                                                           String operator) {
        Path draftDir = getDraftsRoot().resolve(draftId);

        if (!Files.exists(draftDir)) {
            throw new RuntimeException("草稿不存在: " + draftId);
        }

        try {
            // 检查过期
            SkillInstallDraft draft = loadDraft(draftId);
            if (draft.getExpiresAt() != null && draft.getExpiresAt().isBefore(LocalDateTime.now())) {
                discardDraft(draftId, operator);
                throw new RuntimeException("草稿已过期");
            }

            // 规范默认 shareConfig
            if (shareConfig == null) {
                shareConfig = Map.of("accessLevel", "user", "departmentIds", List.of(), "userUids", List.of());
            }

            List<SkillInstallDraft.DraftItem> results = new ArrayList<>();

            try (Stream<Path> dirs = Files.list(draftDir)) {
                List<Path> skillDirs = dirs
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("metadata.json"))
                    .collect(Collectors.toList());

                for (Path skillItemDir : skillDirs) {
                    String slug = skillItemDir.getFileName().toString();
                    SkillInstallDraft.DraftItem item = new SkillInstallDraft.DraftItem();
                    item.setSlug(slug);

                    try {
                        // 解析 SKILL.md
                        Path skillMdPath = skillItemDir.resolve("SKILL.md");
                        if (!Files.exists(skillMdPath)) {
                            item.setSuccess(false);
                            item.setErrorMsg("缺少 SKILL.md");
                            results.add(item);
                            continue;
                        }

                        String content = Files.readString(skillMdPath);
                        SkillMarkdownParser.SkillParseResult parseResult = markdownParser.parse(content);

                        // 如果 slug 已存在，先删除旧的（覆盖重装）
                        Skill existing = skillMapper.selectBySlug(parseResult.getSlug());
                        if (existing != null) {
                            log.info("[SkillDraft] 覆盖重装技能: {} ({})", parseResult.getName(), parseResult.getSlug());
                            // 删除旧的磁盘目录
                            Path oldDir = getSkillsRoot().resolve(parseResult.getSlug());
                            if (Files.exists(oldDir)) deleteDirectory(oldDir);
                            // 删除旧的 DB 记录（级联删除依赖和范围）
                            skillMapper.deleteById(existing.getId());
                        }

                        // 目标目录: 如有残留则清理
                        Path targetDir = getSkillsRoot().resolve(parseResult.getSlug());
                        if (Files.exists(targetDir)) {
                            log.warn("[SkillDraft] 清理残留目录: {}", targetDir);
                            deleteDirectory(targetDir);
                        }
                        Files.createDirectories(targetDir.getParent());
                        copyDirectory(skillItemDir, targetDir);

                        // 创建 DB 记录
                        Skill s = new Skill();
                        s.setSlug(parseResult.getSlug());
                        s.setName(parseResult.getName());
                        s.setDescription(parseResult.getDescription());
                        s.setSourceType("upload");
                        s.setIsBuiltin(0);
                        s.setEnabled(1);
                        s.setRecommended(0);
                        s.setContent(content);
                        s.setContentHash(DigestUtils.md5DigestAsHex(content.getBytes()));
                        s.setMetadata(parseResult.getMetadata());
                        s.setShareConfig(shareConfig);
                        s.setVersion(parseResult.getVersion());
                        s.setCategory(parseResult.getCategory());
                        s.setAuthor(operator);

                        skillMapper.insert(s);

                        item.setSlug(parseResult.getSlug());
                        item.setName(parseResult.getName());
                        item.setDescription(parseResult.getDescription());
                        item.setSuccess(true);
                        item.setMetadata(parseResult.getMetadata());
                        log.info("[SkillDraft] 安装技能: {} ({})", parseResult.getName(), parseResult.getSlug());

                    } catch (Exception e) {
                        log.error("[SkillDraft] 安装失败: {}", slug, e);
                        item.setSuccess(false);
                        item.setErrorMsg(e.getMessage());
                    }
                    results.add(item);
                }
            }

            // 清理草稿
            deleteDirectory(draftDir);

            return results;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("确认安装失败", e);
        }
    }

    @Override
    public void discardDraft(String draftId, String operator) {
        Path draftDir = getDraftsRoot().resolve(draftId);
        if (Files.exists(draftDir)) {
            try {
                deleteDirectory(draftDir);
                log.info("[SkillDraft] 丢弃草稿: draftId={} (by {})", draftId, operator);
            } catch (IOException e) {
                log.error("[SkillDraft] 丢弃草稿失败: {}", draftId, e);
            }
        }
    }

    // === 辅助方法 ===

    private void extractZip(byte[] fileBytes, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // 安全检查
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new SecurityException("ZIP 中包含非法路径: " + name);
                }
                Path target = targetDir.resolve(name).normalize();
                if (!target.startsWith(targetDir.normalize())) {
                    throw new SecurityException("路径遍历攻击检测: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 如果 draftDir 根目录下直接有 SKILL.md（单技能 ZIP 没有子目录），
     * 读取 slug 后整理到 {slug}/ 子目录。
     */
    private void reorganizeSingleSkill(Path draftDir) throws IOException {
        Path rootSkillMd = draftDir.resolve("SKILL.md");
        if (!Files.exists(rootSkillMd)) return;

        // 读取 slug
        String content = Files.readString(rootSkillMd);
        SkillMarkdownParser.SkillParseResult result = markdownParser.parse(content);
        String slug = result.getSlug();

        Path slugDir = draftDir.resolve(slug);
        Files.createDirectories(slugDir);

        // 移动 SKILL.md 到 slug 目录
        Files.move(rootSkillMd, slugDir.resolve("SKILL.md"), StandardCopyOption.REPLACE_EXISTING);

        // 移动其他文件/目录（如 references/、scripts/）
        // 注意：目录不能用 Files.move（Windows 上非空目录抛 AccessDeniedException）
        try (Stream<Path> allFiles = Files.list(draftDir)) {
            allFiles
                .filter(p -> !p.equals(slugDir) && !p.getFileName().toString().startsWith(".") && !p.getFileName().toString().equals("metadata.json"))
                .forEach(p -> {
                    try {
                        Path target = slugDir.resolve(p.getFileName());
                        if (Files.isDirectory(p)) {
                            // 目录用递归复制+删除，而非Files.move
                            copyDirectory(p, target);
                            deleteDirectory(p);
                        } else {
                            Files.createDirectories(target.getParent());
                            Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        log.debug("[SkillDraft] 移动文件: {} -> {}", p.getFileName(), target);
                    } catch (IOException e) {
                        log.warn("[SkillDraft] 移动文件失败: {} -> {}", p.getFileName(), slugDir, e);
                    }
                });
        }

        log.debug("[SkillDraft] 单技能 ZIP 已整理: {} -> {}/", slug, slug);
    }

    private List<SkillInstallDraft.DraftItem> scanSkillItems(Path draftDir) throws IOException {
        List<SkillInstallDraft.DraftItem> items = new ArrayList<>();
        if (!Files.exists(draftDir)) return items;

        try (Stream<Path> dirs = Files.list(draftDir)) {
            List<Path> subDirs = dirs.filter(Files::isDirectory).collect(Collectors.toList());

            for (Path subDir : subDirs) {
                // 跳过 metadata.json 所在的目录... 实际上 subDir 本身就是子目录
                Path skillMd = subDir.resolve("SKILL.md");
                if (Files.exists(skillMd)) {
                    parseAndAdd(items, subDir, skillMd);
                }
            }
        }
        return items;
    }

    private void parseAndAdd(List<SkillInstallDraft.DraftItem> items, Path baseDir, Path skillMd) {
        try {
            String content = Files.readString(skillMd);
            SkillMarkdownParser.SkillParseResult result = markdownParser.parse(content);

            SkillInstallDraft.DraftItem item = new SkillInstallDraft.DraftItem();
            item.setSlug(result.getSlug());
            item.setName(result.getName());
            item.setDescription(result.getDescription());
            item.setSuccess(true);
            item.setMetadata(result.getMetadata());
            items.add(item);
        } catch (Exception e) {
            log.warn("[SkillDraft] 解析 SKILL.md 失败: {}", skillMd, e);
            SkillInstallDraft.DraftItem item = new SkillInstallDraft.DraftItem();
            item.setSlug(baseDir.getFileName().toString());
            item.setSuccess(false);
            item.setErrorMsg("SKILL.md 解析失败: " + e.getMessage());
            items.add(item);
        }
    }

    private SkillInstallDraft loadDraft(String draftId) throws IOException {
        Path metaFile = getDraftsRoot().resolve(draftId).resolve("metadata.json");
        if (!Files.exists(metaFile)) {
            throw new RuntimeException("草稿元数据不存在: " + draftId);
        }
        return objectMapper.readValue(Files.readString(metaFile), SkillInstallDraft.class);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(p -> {
                try {
                    Path dest = target.resolve(source.relativize(p));
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("复制失败: " + p, e);
                }
            });
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
            }
        }
    }

    private void cleanupExpiredDrafts() {
        Path draftsRoot = getDraftsRoot();
        if (!Files.exists(draftsRoot)) return;
        try (Stream<Path> dirs = Files.list(draftsRoot)) {
            dirs.filter(Files::isDirectory).forEach(draftDir -> {
                try {
                    Path metaFile = draftDir.resolve("metadata.json");
                    if (Files.exists(metaFile)) {
                        SkillInstallDraft draft = objectMapper.readValue(Files.readString(metaFile), SkillInstallDraft.class);
                        if (draft.getExpiresAt() != null && draft.getExpiresAt().isBefore(LocalDateTime.now())) {
                            deleteDirectory(draftDir);
                            log.debug("[SkillDraft] 清理过期草稿: {}", draftDir.getFileName());
                        }
                    }
                } catch (Exception e) {
                    log.debug("[SkillDraft] 清理草稿失败: {}", draftDir.getFileName(), e);
                }
            });
        } catch (IOException e) {
            log.warn("[SkillDraft] 扫描草稿目录失败", e);
        }
    }
}
