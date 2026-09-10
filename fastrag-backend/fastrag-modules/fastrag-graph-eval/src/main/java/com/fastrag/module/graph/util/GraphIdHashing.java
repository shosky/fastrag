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
 *   <li>实体 ID：{@code SHA-256(kbId:normalizedName)}——不含类型（Neo4j 实体节点按
 *       (kbId, name) 合并，类型漂移不得影响实体身份；2026-09-06 修复边引用悬空问题）</li>
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
     * 计算实体 ID = SHA-256(kbId:normalizedName)。
     *
     * <p>实体标识刻意<b>不含类型</b>：Neo4j 按 (kbId, name) MERGE 实体节点——同名即同一实体，
     * 类型只是节点属性。若类型参与哈希，同名实体跨 chunk 以不同类型出现时，关系边写入的
     * sourceId/targetId（写入时按当时类型现算）会与节点已存的 entityId（首建时写入）不一致，
     * 造成边引用悬空、前端按 id 关联失败。名称唯一决定身份后，类型漂移不再影响 ID。</p>
     */
    public static String entityId(String kbId, String normalizedName) {
        return hashstr32(kbId + ":" + normalizedName);
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
