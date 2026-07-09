package com.fastrag.module.graph.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 知识图谱确定性 ID 哈希工具（参考 Yuxi IdHashing）
 * <p>使用 SHA-256 哈希算法生成确定性实体 ID 和三元组 ID。
 * 同一输入始终产生相同的 ID，保证跨系统一致性。</p>
 */
public final class GraphIdHashing {

    private GraphIdHashing() {}

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 计算实体 ID = SHA-256(kbId:normalizedName:label)，截断前 32 位
     */
    public static String entityId(String kbId, String normalizedName, String label) {
        return hashstr32(kbId + ":" + normalizedName + ":" + label);
    }

    /**
     * 计算三元组 ID = SHA-256(kbId:sourceName:sourceLabel:relationType:targetName:targetLabel)，截断前 32 位
     */
    public static String tripleId(String kbId, String sourceName, String sourceLabel,
                                 String relationType, String targetName, String targetLabel) {
        return hashstr32(kbId + ":" + sourceName + ":" + sourceLabel + ":"
                + relationType + ":" + targetName + ":" + targetLabel);
    }

    /**
     * SHA-256 哈希并截断到指定长度（默认 32 字符）
     */
    public static String hashstr32(String input) {
        return hashstr(input, 32);
    }

    public static String hashstr(String input, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < Math.min(length, hash.length); i++) {
                sb.append(HEX_CHARS[(hash[i] >> 4) & 0x0F]);
                sb.append(HEX_CHARS[hash[i] & 0x0F]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
