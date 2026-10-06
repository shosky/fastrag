package com.fastrag.module.agent.sandbox;

import java.util.List;

/**
 * 沙箱工作区布局——容器内路径的单一事实源（REQ-H12，2026-09-28 grill 裁决）。
 * <p>
 * 所有后端的沙箱容器内遵循统一布局：
 * <pre>
 *   /workspace                  根（可读可写，shell 默认工作目录）
 *   /workspace/input            只读附件（附件暂存目标，工具面拒写）
 *   /workspace/output           产物收集目录（一期仅布局保留，收集链路二期）
 *   /workspace/skills/{slug}    技能只读子树（工具面拒写）
 * </pre>
 * 旧 {@code /home/gem/*} 布局废止；ContextBuilder / FilesystemMiddleware / SkillsMiddleware
 * 的路径常量一律引用本类，禁止散落字面量。
 */
public final class SandboxWorkspaceLayout {

    public static final String ROOT = "/workspace";
    public static final String INPUT_DIR = "/workspace/input";
    public static final String OUTPUT_DIR = "/workspace/output";
    public static final String SKILLS_ROOT = "/workspace/skills";

    /** 可读根：/workspace 全部（output/input/skills 均在其下） */
    private static final List<String> READ_ROOTS = List.of(ROOT);
    /** 附加拒写子树：附件只读、技能只读 */
    private static final List<String> READ_ONLY_SUBTREES = List.of(INPUT_DIR, SKILLS_ROOT);

    private SandboxWorkspaceLayout() {
    }

    /**
     * 规范化路径：反斜杠转正斜杠、折叠连续斜杠、去掉尾斜杠。不解析「..」（那是 clamp 的职责）。
     */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return ROOT;
        }
        String p = path.replace('\\', '/').replaceAll("/+", "/");
        while (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** 可读判定：/workspace 全部可读（REQ-H12）。 */
    public static boolean isReadable(String path) {
        String p = normalize(path);
        return READ_ROOTS.stream().anyMatch(p::equals) || READ_ROOTS.stream().anyMatch(p::startsWith);
    }

    /**
     * 可写判定：/workspace 下除 input 与 skills 子树（REQ-H12）。
     * 根本身是目录而非可写文件路径，调用方语义层处理。
     */
    public static boolean isWritable(String path) {
        String p = normalize(path);
        if (!isReadable(p)) {
            return false;
        }
        return READ_ONLY_SUBTREES.stream().noneMatch(p::equals)
                && READ_ONLY_SUBTREES.stream().noneMatch(p::startsWith);
    }

    /**
     * 路径钳制（REQ-H12）：清洗 → 规范化 → 拒绝穿越与保留路径。
     *
     * @return 规范化后的安全绝对路径
     * @throws SecurityException 路径含「..」越界、或解析后落在 /workspace 之外
     */
    public static String clamp(String path) {
        String p = normalize(path);
        if (p.contains("..")) {
            throw new SecurityException("Path traversal not allowed: " + path);
        }
        if (!isReadable(p)) {
            throw new SecurityException("Path outside workspace: " + path);
        }
        return p;
    }

    /** 工具描述中的路径提示（FilesystemMiddleware 工具 description 由本方法生成）。 */
    public static String hint() {
        return "Workspace root is " + ROOT + "; attachments are read-only under " + INPUT_DIR
                + "; artifacts under " + OUTPUT_DIR + "; installed skills under " + SKILLS_ROOT;
    }
}
