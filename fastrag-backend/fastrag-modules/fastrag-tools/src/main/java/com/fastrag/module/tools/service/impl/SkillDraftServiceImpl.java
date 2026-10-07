package com.fastrag.module.tools.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependency;
import com.fastrag.module.tools.entity.SkillDependencyInfo;
import com.fastrag.module.tools.entity.SkillInstallDraft;
import com.fastrag.module.tools.mapper.SkillDependencyMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.SkillDraftService;
import com.fastrag.module.tools.skill.SkillAccessManager;
import com.fastrag.module.tools.skill.SkillDependencyValidator;
import com.fastrag.module.tools.skill.SkillMarkdownParser;
import com.fastrag.module.tools.skill.SlugValidator;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
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
    private final SkillAccessManager accessManager;
    private final SkillDependencyMapper dependencyMapper;
    private final SkillDependencyValidator dependencyValidator;

    @Value("${fastrag.data-dir:${user.home}/.fastrag/data}")
    private String dataDir;

    private static final int DRAFT_TTL_HOURS = 1;
    private static final String DRAFTS_DIR = "skill_import_drafts";
    private static final String SKILLS_DIR = "skills";
    /** draftId 由 UUID 生成，入口须匹配 UUID 格式（防路径遍历） */
    private static final java.util.regex.Pattern DRAFT_ID_PATTERN =
        java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private void requireValidDraftId(String draftId) {
        if (draftId == null || !DRAFT_ID_PATTERN.matcher(draftId).matches()) {
            throw new RuntimeException("非法的草稿 ID: " + draftId);
        }
    }

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
                // 单文件上传：在草稿中创建以文件名命名的目录（slug 先过白名单，防路径遍历）
                String slug = SlugValidator.validate(filename.replace(".md", "").replace(".markdown", ""));
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
            cleanupEmptyDraftDirQuietly(draftDir);
            throw e;
        } catch (com.fastrag.common.exception.BusinessException e) {
            // 统一业务错误原样上抛，不被包装丢失 code/文案
            cleanupEmptyDraftDirQuietly(draftDir);
            throw e;
        } catch (Exception e) {
            cleanupEmptyDraftDirQuietly(draftDir);
            throw new RuntimeException("创建安装草稿失败", e);
        }
    }

    /**
     * M3-4（M1 遗留）：prepareUpload 异常通路 best-effort 删除已创建的空草稿目录
     * （无 metadata.json 即无效草稿；cleanupExpiredDrafts 不清理此类目录，须在源头清）。
     */
    private void cleanupEmptyDraftDirQuietly(Path draftDir) {
        try {
            if (draftDir != null && Files.exists(draftDir)
                    && !Files.exists(draftDir.resolve("metadata.json"))) {
                deleteDirectory(draftDir);
                log.debug("[SkillDraft] 已清理异常通路残留的空草稿目录: {}", draftDir);
            }
        } catch (Exception cleanupEx) {
            log.debug("[SkillDraft] 空草稿目录清理失败（best-effort）: {}", cleanupEx.getMessage());
        }
    }

    @Override
    @Transactional
    public List<SkillInstallDraft.DraftItem> confirmDraft(String draftId,
                                                           Map<String, Object> shareConfig,
                                                           String operator) {
        requireValidDraftId(draftId);
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

            // 规范默认 shareConfig；M3-1（V2）：非管理员（无 * 权限且非 API Token）提交
            // global/department 强制降级 user（归一规则复用 SkillAccessManager）
            shareConfig = accessManager.normalizeShareConfig(
                    shareConfig != null ? shareConfig : Map.of("accessLevel", "user"),
                    SecurityUtil.getCurrentUser());

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
                        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes());

                        // M3-5（D11）：slug 已存在 → update-in-place（主键保留，app_skill_binding 天然存活），
                        // 不再 deleteById+insert（曾造成绑定孤儿）；scope 行保留（不触碰）
                        Skill existing = skillMapper.selectBySlug(parseResult.getSlug());
                        String existingId = existing != null ? existing.getId() : null;

                        // M3-2（D9）：解析出的依赖先经校验器验证（非法项不落库、不阻断安装——
                        // 技能可先于其依赖的工具/MCP 存在），主键确定后写 skill_dependency 行 + dependencies JSON
                        List<SkillDependencyInfo> validDeps = validateDependencies(existingId, parseResult);

                        // 目标目录: 如有残留则清理
                        Path targetDir = getSkillsRoot().resolve(parseResult.getSlug());
                        if (Files.exists(targetDir)) {
                            log.warn("[SkillDraft] 清理残留目录: {}", targetDir);
                            deleteDirectory(targetDir);
                        }
                        Files.createDirectories(targetDir.getParent());
                        copyDirectory(skillItemDir, targetDir);

                        List<String> depRefs = dependencyRefsOf(validDeps);
                        if (existing != null) {
                            log.info("[SkillDraft] 覆盖重装技能（in-place）: {} ({})",
                                    parseResult.getName(), parseResult.getSlug());
                            existing.setName(parseResult.getName());
                            existing.setDescription(parseResult.getDescription());
                            existing.setContent(content);
                            existing.setContentHash(contentHash);
                            existing.setVersion(parseResult.getVersion());
                            existing.setCategory(parseResult.getCategory());
                            existing.setMetadata(parseResult.getMetadata());
                            existing.setAuthor(operator);
                            existing.setDependencies(depRefs.isEmpty() ? null : depRefs);
                            // F4：env 声明随 SKILL.md 全量重写（无声明清空旧值）
                            existing.setEnvs(envsOf(parseResult));
                            skillMapper.updateById(existing);
                            writeDependencyRows(existingId, validDeps);
                        } else {
                            // 新装：建 DB 记录（属主对齐 SkillServiceImpl.create 口径：creator=operator，orgId=当前用户组织）
                            LoginUser currentUser = SecurityUtil.getCurrentUser();
                            Skill s = new Skill();
                            s.setCreator(operator);
                            s.setOrgId(currentUser.getOrgId());
                            s.setSlug(parseResult.getSlug());
                            s.setName(parseResult.getName());
                            s.setDescription(parseResult.getDescription());
                            s.setSourceType("upload");
                            s.setIsBuiltin(0);
                            s.setEnabled(1);
                            s.setRecommended(0);
                            s.setContent(content);
                            s.setContentHash(contentHash);
                            s.setMetadata(parseResult.getMetadata());
                            s.setShareConfig(shareConfig);
                            s.setVersion(parseResult.getVersion());
                            s.setCategory(parseResult.getCategory());
                            s.setAuthor(operator);
                            s.setDependencies(depRefs.isEmpty() ? null : depRefs);
                            // F4：env 声明落库（仅变量名，值运行期从进程环境解析）
                            s.setEnvs(envsOf(parseResult));
                            skillMapper.insert(s);
                            writeDependencyRows(s.getId(), validDeps);
                        }

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
        requireValidDraftId(draftId);
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

    /**
     * M3-2（D9）：frontmatter 解析出的依赖经 {@link SkillDependencyValidator} 校验。
     * 非法项仅记 warn 不阻断安装（技能可先于其依赖存在），返回合法依赖列表。
     */
    private List<SkillDependencyInfo> validateDependencies(String skillId,
                                                           SkillMarkdownParser.SkillParseResult parseResult) {
        List<SkillDependencyInfo> infos = new ArrayList<>();
        for (String t : parseResult.getToolDependencies()) {
            SkillDependencyInfo i = new SkillDependencyInfo();
            i.setType("tool"); i.setName(t); i.setRequired(true); infos.add(i);
        }
        for (String m : parseResult.getMcpDependencies()) {
            SkillDependencyInfo i = new SkillDependencyInfo();
            i.setType("mcp"); i.setName(m); i.setRequired(true); infos.add(i);
        }
        for (String sk : parseResult.getSkillDependencies()) {
            SkillDependencyInfo i = new SkillDependencyInfo();
            i.setType("skill"); i.setName(sk); i.setRequired(true); infos.add(i);
        }
        if (infos.isEmpty()) return List.of();

        SkillDependencyValidator.ValidationResult vr = dependencyValidator.validate(skillId, infos);
        if (vr.getErrors() != null && !vr.getErrors().isEmpty()) {
            log.warn("[SkillDraft] 部分依赖未通过校验（跳过不阻断）: {}", String.join("; ", vr.getErrors()));
        }
        return vr.getValidDeps() != null ? vr.getValidDeps() : List.of();
    }

    /** M3-2：合法依赖转 {@code type:name} 规范依赖串（与 Skill.dependencies JSON 约定一致） */
    private List<String> dependencyRefsOf(List<SkillDependencyInfo> validDeps) {
        if (validDeps == null || validDeps.isEmpty()) return List.of();
        return validDeps.stream().map(d -> d.getType() + ":" + d.getName()).toList();
    }

    /**
     * F4：frontmatter env 声明转 {@code Skill.envs}（变量名列表 JSON）。
     * 空列表归 null（不写空数组，与 dependencies 空值语义一致）。
     */
    private List<String> envsOf(SkillMarkdownParser.SkillParseResult parseResult) {
        List<String> envs = parseResult.getEnvs();
        return envs == null || envs.isEmpty() ? null : envs;
    }

    /** M3-2：依赖行先删后插（主键确定后调用；新装主键来自 ASSIGN_ID insert 回填） */
    private void writeDependencyRows(String skillId, List<SkillDependencyInfo> validDeps) {
        if (skillId == null) return;
        dependencyMapper.deleteBySkillId(skillId);
        if (validDeps == null) return;
        for (SkillDependencyInfo d : validDeps) {
            SkillDependency dep = new SkillDependency();
            dep.setSkillId(skillId);
            dep.setType(d.getType());
            dep.setName(d.getName());
            dep.setRequired(d.isRequired() ? 1 : 0);
            dependencyMapper.insert(dep);
        }
    }

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

        // 读取 slug（白名单校验防路径遍历）
        String content = Files.readString(rootSkillMd);
        SkillMarkdownParser.SkillParseResult result = markdownParser.parse(content);
        String slug = SlugValidator.validate(result.getSlug());

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
        requireValidDraftId(draftId);
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
