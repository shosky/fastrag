package com.fastrag.module.graph.util;

/**
 * 实体名称标准化工具类。
 *
 * <p>将实体名称统一标准化为小写形式，并去除首尾空白、合并连续空白字符，
 * 确保同一实体的不同书写方式（如大小写差异、多余空格）产生完全相同的标准化结果。
 * 这是图谱构建流程中实体去重和确定性 ID 生成的底层基础：所有实体名称在写入
 * {@link com.fastrag.module.graph.entity.KbGraphEntity} 之前均需经过本工具标准化处理，
 * 随后传入 {@link GraphIdHashing} 计算确定性哈希 ID。</p>
 *
 * <p>标准化规则为：先 trim() 去除首尾空白，再 toLowerCase() 转为小写，
 * 最后将所有连续空白字符合并为单个空格。输入为 null 或空字符串时返回空字符串。
 * 本类为无状态工具类，仅包含静态方法，被 {@link ExtractionNormalizer}、
 * {@link GraphIdHashing} 等多个工具类和图谱构建流程广泛依赖。</p>
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
