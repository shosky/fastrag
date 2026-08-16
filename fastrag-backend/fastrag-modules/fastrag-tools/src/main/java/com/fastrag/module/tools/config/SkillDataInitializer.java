package com.fastrag.module.tools.config;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.skill.SkillMarkdownParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内置技能种子数据初始化器。
 *
 * <p>实现 {@link CommandLineRunner}，在 Spring Boot 启动时自动执行，
 * 从 classpath {@code builtin-skills/} 目录下扫描所有子目录的 {@code SKILL.md} 文件，
 * 通过 {@link SkillMarkdownParser} 解析后与数据库（{@code skill} 表）进行增量同步。</p>
 *
 * <h3>核心实现逻辑：</h3>
 * <ul>
 *   <li>资源扫描：使用 {@link PathMatchingResourcePatternResolver} 匹配
 *       {@code classpath:builtin-skills/&#42;/SKILL.md}，加载每个技能目录的 SKILL.md 内容</li>
 *   <li>内容哈希：对 SKILL.md 做 MD5 摘要，若哈希一致则跳过更新（避免无变化写入）</li>
 *   <li>增量同步：slug 查询无记录时 insert（标记 builtin+recommended），
 *       已有记录且哈希变化时 update（保留 enabled、shareConfig 等用户可修改字段）</li>
 *   <li>文件复制：将整个技能目录（SKILL.md + references/ + scripts/）复制到
 *       {@code ${fastrag.data-dir}/skills/{slug}/} 运行时数据目录，供技能执行时引用</li>
 * </ul>
 *
 * @see Skill
 * @see SkillMapper
 * @see SkillMarkdownParser
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillDataInitializer implements CommandLineRunner {

    private final SkillMapper skillMapper;
    private final SkillMarkdownParser markdownParser;

    @Value("${fastrag.data-dir:${user.home}/.fastrag/data}")
    private String dataDir;

    private static final String BUILTIN_SKILLS_PATH = "classpath:builtin-skills/*/SKILL.md";

    @Override
    public void run(String... args) {
        log.info("[SkillDataInitializer] 开始初始化内置技能...");

        List<BuiltinSkillSpec> specs = loadBuiltinSkillSpecs();
        int created = 0;
        int updated = 0;

        for (BuiltinSkillSpec spec : specs) {
            try {
                if (syncBuiltinSkill(spec)) {
                    created++;
                } else {
                    updated++;
                }
                // 复制整个技能目录到运行时数据目录
                copySkillDir(spec);
            } catch (Exception e) {
                log.error("[SkillDataInitializer] 同步技能失败: {}", spec.getSlug(), e);
            }
        }

        log.info("[SkillDataInitializer] 内置技能初始化完成。创建 {} 个，更新 {} 个，共 {} 个",
            created, updated, specs.size());
    }

    private List<BuiltinSkillSpec> loadBuiltinSkillSpecs() {
        List<BuiltinSkillSpec> specs = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(BUILTIN_SKILLS_PATH);

            for (Resource resource : resources) {
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                String content = new String(bytes, StandardCharsets.UTF_8);
                String contentHash = DigestUtils.md5DigestAsHex(bytes);

                SkillMarkdownParser.SkillParseResult result = markdownParser.parse(content);

                // 获取技能目录的 classpath 路径
                String url = resource.getURL().toString();
                String dirUrl = url.substring(0, url.lastIndexOf('/'));
                // 去掉 classpath 协议前缀
                String cpPath = dirUrl;
                if (cpPath.contains("builtin-skills")) {
                    cpPath = cpPath.substring(cpPath.indexOf("builtin-skills"));
                }

                BuiltinSkillSpec spec = new BuiltinSkillSpec();
                spec.setSlug(result.getSlug());
                spec.setName(result.getName());
                spec.setDescription(result.getDescription());
                spec.setVersion(result.getVersion());
                spec.setCategory(result.getCategory());
                spec.setIcon(result.getIcon());
                spec.setMetadata(result.getMetadata());
                spec.setContent(content);
                spec.setContentHash(contentHash);
                spec.setClasspathDir(cpPath);
                specs.add(spec);

                log.debug("[SkillDataInitializer] 加载内置技能: {} ({})", result.getName(), result.getSlug());
            }
        } catch (Exception e) {
            log.error("[SkillDataInitializer] 加载内置技能资源失败", e);
        }
        return specs;
    }

    /**
     * 将技能完整目录（SKILL.md + references/ + scripts/）复制到运行时数据目录
     */
    private void copySkillDir(BuiltinSkillSpec spec) {
        try {
            Path targetDir = Paths.get(dataDir, "skills", spec.getSlug());
            Files.createDirectories(targetDir);

            // 从 classpath 复制整个目录
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = "classpath:" + spec.getClasspathDir() + "/**";
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                String url = resource.getURL().toString();
                // 计算相对路径
                String cpDir = spec.getClasspathDir().replace("\\", "/");
                int idx = url.indexOf(cpDir);
                if (idx < 0) continue;
                String relativePath = url.substring(idx + cpDir.length());
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

                Path target = targetDir.resolve(relativePath).normalize();
                if (!target.startsWith(targetDir.normalize())) continue; // 安全防护

                if (resource.isReadable()) {
                    Files.createDirectories(target.getParent());
                    try (InputStream is = resource.getInputStream()) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    log.debug("[SkillDataInitializer] 复制文件: {} -> {}", relativePath, target);
                }
            }

            log.info("[SkillDataInitializer] 技能目录已同步: {} ({} 文件)", spec.getSlug(),
                Files.walk(targetDir).filter(Files::isRegularFile).count());

        } catch (Exception e) {
            log.error("[SkillDataInitializer] 复制技能目录失败: {}", spec.getSlug(), e);
        }
    }

    private boolean syncBuiltinSkill(BuiltinSkillSpec spec) {
        Skill existing = skillMapper.selectBySlug(spec.getSlug());

        if (existing != null) {
            if (spec.getContentHash().equals(existing.getContentHash())) {
                log.debug("[SkillDataInitializer] 技能无变化: {}", spec.getSlug());
                return false;
            }
            existing.setName(spec.getName());
            existing.setDescription(spec.getDescription());
            existing.setContent(spec.getContent());
            existing.setContentHash(spec.getContentHash());
            existing.setVersion(spec.getVersion());
            existing.setCategory(spec.getCategory());
            existing.setIcon(spec.getIcon());
            existing.setMetadata(spec.getMetadata());
            existing.setSourceType("builtin");
            existing.setIsBuiltin(1);
            if (existing.getShareConfig() == null || existing.getShareConfig().isEmpty()) {
                existing.setShareConfig(Map.of("accessLevel", "global", "departmentIds", List.of(), "userUids", List.of()));
            }
            skillMapper.updateById(existing);
            log.info("[SkillDataInitializer] 更新内置技能: {}", spec.getSlug());
            return false;
        } else {
            Skill s = new Skill();
            s.setSlug(spec.getSlug());
            s.setName(spec.getName());
            s.setDescription(spec.getDescription());
            s.setSourceType("builtin");
            s.setIsBuiltin(1);
            s.setEnabled(1);
            s.setRecommended(1);
            s.setContent(spec.getContent());
            s.setContentHash(spec.getContentHash());
            s.setVersion(spec.getVersion());
            s.setCategory(spec.getCategory());
            s.setIcon(spec.getIcon());
            s.setMetadata(spec.getMetadata());
            s.setShareConfig(Map.of("accessLevel", "global", "departmentIds", List.of(), "userUids", List.of()));
            skillMapper.insert(s);
            log.info("[SkillDataInitializer] 创建内置技能: {} ({})", spec.getName(), spec.getSlug());
            return true;
        }
    }

    @lombok.Data
    private static class BuiltinSkillSpec {
        private String slug;
        private String name;
        private String description;
        private String version;
        private String category;
        private String icon;
        private Map<String, Object> metadata;
        private String content;
        private String contentHash;
        private String classpathDir;
    }
}
