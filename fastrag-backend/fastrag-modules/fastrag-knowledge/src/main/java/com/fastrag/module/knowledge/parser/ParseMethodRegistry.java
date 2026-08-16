package com.fastrag.module.knowledge.parser;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 解析方法（文档类型）注册表 —— 「文档类型 ↔ 扩展名 ↔ 解析方法」的唯一权威映射。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义系统支持的全部解析方法（即用户可见的「文档类型」），每种方法绑定其兼容的扩展名集</li>
 *   <li>提供扩展名 → 解析方法推断、扩展名支持性校验、扩展名与方法的兼容性校验</li>
 *   <li>前端通过 {@code GET /api/parse-strategies/meta} 获取本注册表渲染策略表单，避免前后端口径漂移</li>
 * </ul>
 *
 * <p>语义说明：
 * <ul>
 *   <li>{@link #DEFAULT} 为「自动识别（混合文档）」：兼容所有受支持扩展名，解析时按扩展名逐个推断方法</li>
 *   <li>其余方法的扩展名集为该方法专用；扩展名与 parseMethod 必须匹配，否则创建/更新策略会被拒绝</li>
 *   <li>扩展名统一存储为小写带点形式（如 {@code ".pdf"}），比较前归一化</li>
 * </ul>
 */
@Getter
public enum ParseMethodRegistry {
    /** 自动识别（混合文档）：兼容所有受支持扩展名，解析时按扩展名自动推断方法 */
    DEFAULT("default", "自动识别（混合文档）",
            List.of(".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".md", ".txt", ".csv")),
    PDF("pdf", "PDF", List.of(".pdf")),
    DOC("doc", "Word 97-2003", List.of(".doc")),
    DOCX("docx", "Word", List.of(".docx")),
    PPTX("pptx", "PPT", List.of(".pptx", ".ppt")),
    XLSX("xlsx", "Excel", List.of(".xlsx", ".xls")),
    VIDEO("video", "视频", List.of(".mp4", ".avi", ".mov", ".mkv", ".flv", ".wmv", ".webm")),
    AUDIO("audio", "音频", List.of(".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac", ".wma")),
    IMAGE("image", "图片", List.of(".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".gif", ".webp"));

    private final String code;
    private final String label;
    /** 该方法兼容的扩展名（带点、小写） */
    private final List<String> extensions;

    ParseMethodRegistry(String code, String label, List<String> extensions) {
        this.code = code;
        this.label = label;
        this.extensions = extensions;
    }

    /** 按方法代码查找，未找到返回 null */
    public static ParseMethodRegistry of(String code) {
        if (code == null) return null;
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst().orElse(null);
    }

    /** 扩展名 → 解析方法代码；未识别时返回 default（纯文本兜底） */
    public static String resolveByExtension(String extension) {
        if (extension == null || extension.isBlank()) return "default";
        String ext = normalize(extension);
        for (ParseMethodRegistry m : values()) {
            if (m != DEFAULT && m.extensions.contains(ext)) return m.code;
        }
        return "default";
    }

    /** 扩展名是否被系统支持（任一方法兼容即支持） */
    public static boolean isSupported(String extension) {
        String ext = normalize(extension);
        return Arrays.stream(values()).anyMatch(m -> m.extensions.contains(ext));
    }

    /** 扩展名是否与解析方法兼容（default 兼容所有受支持扩展名） */
    public static boolean isCompatible(String methodCode, String extension) {
        ParseMethodRegistry m = of(methodCode);
        if (m == null) return false;
        if (m == DEFAULT) return isSupported(extension);
        return m.extensions.contains(normalize(extension));
    }

    /** 全部受支持扩展名（带点、小写、去重保序） */
    public static List<String> supportedExtensions() {
        List<String> all = new ArrayList<>();
        for (ParseMethodRegistry m : values()) {
            for (String ext : m.extensions) {
                if (!all.contains(ext)) all.add(ext);
            }
        }
        return all;
    }

    /** 归一化扩展名：去空格、转小写、补点 */
    private static String normalize(String extension) {
        String e = extension.trim().toLowerCase(Locale.ROOT);
        return e.startsWith(".") ? e : "." + e;
    }
}
