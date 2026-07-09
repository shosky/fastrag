package com.fastrag.module.graph.util;

/**
 * 名称标准化工具（参考 Yuxi NameNormalizer）
 * <p>将实体名称标准化为小写、去除首尾空白、合并连续空白，
 * 确保同一名称的不同写法（大小写、多余空格）产生相同的标准化结果。</p>
 */
public final class NameNormalizer {

    private NameNormalizer() {}

    /**
     * 标准化实体名称：trim + toLowerCase + collapse whitespace
     *
     * @param text 原始名称
     * @return 标准化后的名称
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return String.join(" ", text.trim().toLowerCase().split("\\s+"));
    }
}
