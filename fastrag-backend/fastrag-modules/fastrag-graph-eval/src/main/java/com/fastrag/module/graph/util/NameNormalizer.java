package com.fastrag.module.graph.util;

/**
 * 实体名称标准化工具类。
 *
 * <p>将实体名称统一标准化为去重友好的规范形式，确保同一实体的不同书写方式
 * 产生完全相同的标准化结果。这是图谱构建流程中实体去重和确定性 ID 生成的底层基础：
 * 所有实体名称在写入图存储之前均需经过本工具标准化处理，随后传入
 * {@link GraphIdHashing} 计算确定性哈希 ID。</p>
 *
 * <p>标准化规则（按顺序执行）：</p>
 * <ol>
 *   <li>trim() 去除首尾空白</li>
 *   <li>全角字母/数字/符号归一为半角（（）：＊ＡＢＣ１２３ → ():*ABC123），
 *       避免"（云化版）"与"(云化版)"分裂成两个实体</li>
 *   <li>剥离尾部数量后缀（"*4个"、"×2"、"（4个）"）——数量是设备清单的采购信息，
 *       不属于实体身份（"TP LINK CT4WS-P V2(云化版)*4个" 归一到 "tp link ct4ws-p v2(云化版)"）</li>
 *   <li>toLowerCase() 转为小写</li>
 *   <li>连续空白字符合并为单个空格</li>
 * </ol>
 *
 * <p>注意：名称开头的数量前缀（"4个摄像头"、"1主1从标准包"）不剥离——这类名称
 * 本身携带业务语义，且由 {@link EntityNameGuard} 决定是否可入图。</p>
 *
 * <p>本类为无状态工具类，仅包含静态方法，被 {@link ExtractionNormalizer}、
 * {@link GraphIdHashing} 等多个工具类和图谱构建流程广泛依赖。</p>
 */
public final class NameNormalizer {

    private NameNormalizer() {}

    /** 尾部数量后缀：*4个 / ×2（仅限星号与乘号——字母 x 会误伤 RTX 4090 式型号名，不做乘号） */
    private static final String TRAILING_QUANTITY_SUFFIX = "[*×]\\s*\\d+\\s*个?$";

    /** 尾部括号数量：（4个）/(4个)/（16路） */
    private static final String TRAILING_PAREN_QUANTITY = "[（(]\\s*\\d+\\s*(个|路|口|条)\\s*[）)]$";

    /**
     * 标准化实体名称：trim + 全半角归一 + 剥离尾部数量后缀 + 去CJK相关空格 + toLowerCase + collapse whitespace
     *
     * <p>去CJK相关空格为 LightRAG 同款归一规则（utils.py sanitize_and_normalize_extracted_text）：
     * 去中文字符之间的空格、中文与英文/数字之间的空格——"小微 ICT" 与 "小微ICT"、
     * "华为 B671-S2" 与 "华为B671-S2" 必须归一到同一结果，否则跨 chunk 实体在合并层分裂。
     * 纯英文单词之间的空格保留（TP LINK CT4WS-P V2 不受影响）。</p>
     *
     * @param text 原始名称
     * @return 标准化后的名称
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = toHalfWidth(text.trim());
        // 剥离尾部数量后缀（可能连续出现，如 "*4个(云化版)" 不存在但防御性循环）
        for (int i = 0; i < 3; i++) {
            String stripped = stripTrailingQuantity(s);
            if (stripped.equals(s)) break;
            s = stripped;
        }
        s = s.toLowerCase();
        s = stripCjkAdjacentSpaces(s);
        return String.join(" ", s.split("\\s+"));
    }

    /** 去中文字符之间、中文与英文/数字之间的空格（LightRAG 同款规则） */
    private static String stripCjkAdjacentSpaces(String s) {
        s = s.replaceAll("(?<=[\u4E00-\u9FA5])\\s+(?=[\u4E00-\u9FA5])", "");
        s = s.replaceAll("(?<=[\u4E00-\u9FA5])\\s+(?=[A-Za-z0-9])", "");
        s = s.replaceAll("(?<=[A-Za-z0-9])\\s+(?=[\u4E00-\u9FA5])", "");
        return s;
    }

    /** 全角 ASCII 字符与常用全角符号归一为半角 */
    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 12288) { // 全角空格
                c = ' ';
            } else if (c >= 65281 && c <= 65374) { // 全角 ASCII 区（！ 到 ～）
                c = (char) (c - 65248);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 剥离一种尾部数量后缀；无匹配时原样返回 */
    private static String stripTrailingQuantity(String s) {
        String stripped = s.replaceAll(TRAILING_QUANTITY_SUFFIX, "").replaceAll(TRAILING_PAREN_QUANTITY, "");
        // 防止剥离后只剩空串或纯符号（如名称本身就是 "*2"）：无有效字符则放弃剥离
        if (stripped.trim().isEmpty() || !stripped.matches(".*[\\p{L}\\p{Nd}].*")) {
            return s;
        }
        return stripped.trim();
    }
}
