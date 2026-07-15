package com.fastrag.module.tools.service.impl;

import com.fastrag.module.tools.entity.SkillFileContent;
import com.fastrag.module.tools.entity.SkillFileTree;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.SkillFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillFileServiceImpl implements SkillFileService {

    private final SkillMapper skillMapper;

    @Value("${fastrag.data-dir:${user.home}/.fastrag/data}")
    private String dataDir;

    private Path getSkillDir(String slug) {
        return Paths.get(dataDir, "skills", slug);
    }

    private Path validateAndResolve(String slug, String relativePath) {
        Path skillDir = getSkillDir(slug);
        Path target = skillDir.resolve(relativePath).normalize();
        if (!target.startsWith(skillDir.normalize())) {
            throw new SecurityException("路径遍历攻击检测: " + relativePath);
        }
        return target;
    }

    @Override
    public SkillFileTree getTree(String slug) {
        Path skillDir = getSkillDir(slug);
        if (!Files.exists(skillDir)) {
            // 如果目录不存在，返回空树
            SkillFileTree root = new SkillFileTree();
            root.setName(slug);
            root.setDir(true);
            root.setChildren(List.of());
            return root;
        }
        return buildTree(skillDir);
    }

    private SkillFileTree buildTree(Path dir) {
        SkillFileTree node = new SkillFileTree();
        node.setName(dir.getFileName().toString());
        node.setDir(Files.isDirectory(dir));
        try {
            if (!Files.isDirectory(dir)) {
                node.setSize(Files.size(dir));
            }
            node.setLastModified(Files.getLastModifiedTime(dir).toMillis());
        } catch (IOException e) {
            log.debug("Failed to get file attributes: {}", dir, e);
        }

        if (Files.isDirectory(dir)) {
            try (Stream<Path> children = Files.list(dir)) {
                node.setChildren(children
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .map(this::buildTree)
                    .collect(Collectors.toList()));
            } catch (IOException e) {
                log.error("Failed to list directory: {}", dir, e);
                node.setChildren(List.of());
            }
        }
        return node;
    }

    @Override
    public SkillFileContent readFile(String slug, String relativePath) {
        try {
            Path target = validateAndResolve(slug, relativePath);
            if (!Files.exists(target) || Files.isDirectory(target)) {
                SkillFileContent fc = new SkillFileContent();
                fc.setExists(false);
                fc.setError("文件不存在: " + relativePath);
                return fc;
            }

            SkillFileContent fc = new SkillFileContent();
            fc.setPath(relativePath);
            fc.setContent(Files.readString(target));
            fc.setSize(Files.size(target));
            fc.setLastModified(Files.getLastModifiedTime(target).toMillis());
            fc.setExists(true);
            return fc;
        } catch (SecurityException e) {
            SkillFileContent fc = new SkillFileContent();
            fc.setExists(false);
            fc.setError(e.getMessage());
            return fc;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + relativePath, e);
        }
    }

    @Override
    public void createNode(String slug, String relativePath, boolean isDir, String content, String operator) {
        try {
            Path target = validateAndResolve(slug, relativePath);

            if (Files.exists(target)) {
                throw new RuntimeException("目标已存在: " + relativePath);
            }

            if (isDir) {
                Files.createDirectories(target);
                log.info("[SkillFile] 创建目录: {} (by {})", target, operator);
            } else {
                Files.createDirectories(target.getParent());
                Files.writeString(target, content != null ? content : "");
                log.info("[SkillFile] 创建文件: {} (by {})", target, operator);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("创建失败: " + relativePath, e);
        }
    }

    @Override
    public void updateFile(String slug, String relativePath, String content, String operator) {
        try {
            Path target = validateAndResolve(slug, relativePath);

            if (!Files.exists(target) || Files.isDirectory(target)) {
                throw new RuntimeException("文件不存在: " + relativePath);
            }

            Files.writeString(target, content != null ? content : "");
            log.info("[SkillFile] 更新文件: {} (by {})", target, operator);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("更新文件失败: " + relativePath, e);
        }
    }

    @Override
    public void deleteNode(String slug, String relativePath, String operator) {
        try {
            Path target = validateAndResolve(slug, relativePath);

            if (!Files.exists(target)) {
                throw new RuntimeException("目标不存在: " + relativePath);
            }

            // 禁止删除根目录
            if (target.equals(getSkillDir(slug))) {
                throw new RuntimeException("不能删除技能根目录");
            }

            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                throw new RuntimeException("删除失败: " + p, e);
                            }
                        });
                }
            } else {
                Files.delete(target);
            }
            log.info("[SkillFile] 删除: {} (by {})", target, operator);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("删除失败: " + relativePath, e);
        }
    }

    @Override
    public File exportZip(String slug) {
        Path skillDir = getSkillDir(slug);
        if (!Files.exists(skillDir)) {
            throw new RuntimeException("技能目录不存在: " + slug);
        }

        try {
            File tempFile = File.createTempFile("skill-" + slug + "-", ".zip");
            tempFile.deleteOnExit();

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile));
                 Stream<Path> walk = Files.walk(skillDir)) {

                walk.filter(p -> !Files.isDirectory(p))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        String entryName = skillDir.relativize(p).toString().replace("\\", "/");
                        try {
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("ZIP 写入失败: " + entryName, e);
                        }
                    });
            }

            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("导出 ZIP 失败", e);
        }
    }
}
