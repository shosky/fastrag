package com.fastrag.module.graph.util;

/**
 * 知识图谱确定性 ID 哈希生成工具类。
 *
 * <p>基于 SHA-256 哈希算法为知识图谱中的实体和三元组生成确定性唯一标识符，
 * 是图谱构建和去重机制的核心基础组件。确定性哈希保证：相同的输入始终产生相同的 ID，
 * 从而实现跨批次、跨系统的实体和三元组去重，避免重复插入。</p>
 *
 * <p>ID 生成规则如下：</p>
 * <ul>
 *   <li>实体 ID：{@code SHA-256(kbId:normalizedName:label)}，截断前 32 个十六进制字符，
 *       对应 {@link com.fastrag.module.graph.entity.KbGraphEntity} 的主键字段</li>
 *   <li>三元组 ID：{@code SHA-256(kbId:sourceName:sourceLabel:relationType:targetName:targetLabel)}，
 *       截断前 32 个十六进制字符，对应 {@link com.fastrag.module.graph.entity.KbGraphRelation} 的主键字段</li>
 * </ul>
 *
 * <p>输入名称需先经过 {@link NameNormalizer} 标准化处理后再传入本工具，以确保大小写和空白差异不会产生不同 ID。
 * 32 位截断长度在碰撞概率和可读性之间取得平衡，对于单个知识库规模的实体数量（通常万级以内）碰撞概率极低。
 * 本类为无状态工具类，仅包含静态方法，被图谱构建和去重流程广泛调用。</p>
 */
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
