package com.fastrag.module.knowledge.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 解析方法注册表单元测试。
 *
 * 覆盖：扩展名 → 解析方法推断（含 .ppt → pptx 修复、大小写归一）、
 * 扩展名支持性校验、扩展名与方法的兼容性校验（default 兼容全部）、
 * 支持扩展名全集（去重、含默认文档类）。
 */
class ParseMethodRegistryTest {

    // ========== resolveByExtension ==========

    @Test
    void resolveKnownExtensions() {
        assertEquals("pdf", ParseMethodRegistry.resolveByExtension(".pdf"));
        assertEquals("pdf", ParseMethodRegistry.resolveByExtension("pdf"));
        assertEquals("doc", ParseMethodRegistry.resolveByExtension(".doc"));
        assertEquals("docx", ParseMethodRegistry.resolveByExtension(".docx"));
        assertEquals("pptx", ParseMethodRegistry.resolveByExtension(".pptx"));
        assertEquals("pptx", ParseMethodRegistry.resolveByExtension(".ppt")); // 修复漏映射
        assertEquals("xlsx", ParseMethodRegistry.resolveByExtension(".xlsx"));
        assertEquals("xlsx", ParseMethodRegistry.resolveByExtension(".xls"));
        assertEquals("video", ParseMethodRegistry.resolveByExtension(".mp4"));
        assertEquals("audio", ParseMethodRegistry.resolveByExtension(".mp3"));
        assertEquals("image", ParseMethodRegistry.resolveByExtension(".png"));
    }

    @Test
    void resolveCaseInsensitiveAndTrimmed() {
        assertEquals("pdf", ParseMethodRegistry.resolveByExtension("PDF"));
        assertEquals("video", ParseMethodRegistry.resolveByExtension(" .MP4 "));
    }

    @Test
    void resolveTextAndUnknownFallbackToDefault() {
        assertEquals("default", ParseMethodRegistry.resolveByExtension(".md"));
        assertEquals("default", ParseMethodRegistry.resolveByExtension(".txt"));
        assertEquals("default", ParseMethodRegistry.resolveByExtension(".csv"));
        assertEquals("default", ParseMethodRegistry.resolveByExtension(".unknown"));
        assertEquals("default", ParseMethodRegistry.resolveByExtension(""));
        assertEquals("default", ParseMethodRegistry.resolveByExtension(null));
    }

    // ========== isSupported ==========

    @Test
    void supportedExtensions() {
        assertTrue(ParseMethodRegistry.isSupported(".pdf"));
        assertTrue(ParseMethodRegistry.isSupported("pdf")); // 无点也支持
        assertTrue(ParseMethodRegistry.isSupported(".txt")); // 默认文档类
        assertFalse(ParseMethodRegistry.isSupported(".xyz"));
        assertFalse(ParseMethodRegistry.isSupported(".rtf"));
    }

    // ========== isCompatible ==========

    @Test
    void dedicatedMethodOnlyAcceptsOwnExtensions() {
        assertTrue(ParseMethodRegistry.isCompatible("pdf", ".pdf"));
        assertFalse(ParseMethodRegistry.isCompatible("pdf", ".docx"));
        assertFalse(ParseMethodRegistry.isCompatible("audio", ".mp4")); // 历史 media 模板错配场景
        assertTrue(ParseMethodRegistry.isCompatible("audio", ".mp3"));
    }

    @Test
    void defaultMethodAcceptsAllSupportedExtensions() {
        assertTrue(ParseMethodRegistry.isCompatible("default", ".pdf"));
        assertTrue(ParseMethodRegistry.isCompatible("default", ".mp4"));
        assertTrue(ParseMethodRegistry.isCompatible("default", ".txt"));
        assertFalse(ParseMethodRegistry.isCompatible("default", ".xyz"));
    }

    @Test
    void unknownMethodIsNotCompatible() {
        assertFalse(ParseMethodRegistry.isCompatible("rtf", ".rtf"));
        assertFalse(ParseMethodRegistry.isCompatible(null, ".pdf"));
    }

    // ========== supportedExtensions ==========

    @Test
    void supportedExtensionsUnionIsDeduplicatedAndComplete() {
        var all = ParseMethodRegistry.supportedExtensions();
        // 去重
        assertEquals(all.size(), all.stream().distinct().count());
        // 覆盖默认文档类 + 各专用类型
        assertTrue(all.containsAll(java.util.List.of(
                ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".md", ".txt", ".csv",
                ".mp4", ".wmv", ".webm", ".flac", ".wma", ".webp")));
        // 带点小写
        assertTrue(all.stream().allMatch(e -> e.startsWith(".") && e.equals(e.toLowerCase())));
    }
}
