package com.fastrag.module.graph.util;

/**
 * 实体名清洗闸门（图谱构建写入前的最后一道代码侧防线）。
 *
 * <p>LLM 抽取（含 replay 重放的历史抽取结果）产出的实体名并不总是可信，
 * 本工具在写入 GraphStore 前做纯代码判定，拦截以下几类噪声（均有真实案例支撑）：</p>
 * <ul>
 *   <li>值型内容：纯数值/价格/日期时间/电话与单据号/URL（如 "445元/月"、"ZQ12345"）</li>
 *   <li>键值对残留：表单字段被当成实体（如 "商品数量：10"、"业务号：XWICT07311101174"，
 *       全半角冒号均拦截）</li>
 *   <li>OCR/幻觉乱码：中日文假名与西里尔字符混杂（如 "SiGi 廉控状态Aライザーロール"、
 *       "移动окрайвер"，来自 CRM 界面截图 OCR 噪声或模型幻觉）</li>
 *   <li>金额后缀名：表格碎片截断出的伪产品名（如 "头标准包2999元"）</li>
 *   <li>表头泛化词：BOM 价目表的列头被抽成实体后成为垃圾汇聚点
 *       （如 "总价"、"价格"、"型号"、"工费"）</li>
 *   <li>代码/UI 标识符与截断句碎片（如 "local-btn"、"一次性费用由"）</li>
 * </ul>
 *
 * <p>保守策略：名字含中文或字母组合即保留（如 "4个摄像头"、"5G网优"、"FTTR-B全光组网"），
 * 只拦截明确不可信的模式。本类为无状态工具类，仅含静态方法，
 * 被 {@code GraphBuildConsumer} 在实体写入与关系端点校验时调用。</p>
 */
public final class EntityNameGuard {

    private EntityNameGuard() {}

    /** BOM 价目表/表单的表头泛化词：被抽成实体会形成"总价→价格"式垃圾汇聚节点，精确匹配拦截 */
    private static final java.util.Set<String> GENERIC_TABLE_HEADER_WORDS = java.util.Set.of(
            "价格", "总价", "单价", "合计", "金额", "费用", "工费", "辅材", "型号", "数量", "设备", "费率",
            "费用类型", "设备组成", "产品名称", "标准资费", "叠加费用", "备注", "说明"
    );

    /**
     * 判定实体名是否为垃圾内容，不可入图。
     * 判定规则与拦截案例详见类注释；输入 null 视为垃圾。
     */
    public static boolean isJunkName(String name) {
        return isJunkName(name, null);
    }

    /**
     * 带类型判定的重载：归一化类型为"属性"的实体整体拦截。
     *
     * <p>生产数据教训：截图表单字段被抽成无冒号的"属性"实体（客户联系/失败时间/受理时间），
     * 仅凭名称模式无法拦截，必须借类型信号。类型传 null 时退化为纯名称判定。</p>
     */
    public static boolean isJunkName(String name, String normalizedType) {
        // 带类型判定优先：截图表单字段被抽成无冒号的"属性"实体（客户联系/失败时间/受理时间），
        // 仅凭名称模式无法拦截，必须借类型信号（类型为白名单归一后的精确值）
        if ("属性".equals(normalizedType)) return true;
        if (name == null) return true;
        String s = name.trim();
        if (s.length() < 2) return true;
        if (s.startsWith("http://") || s.startsWith("https://")) return true;

        // 跨语言乱码：中文字域文本中不应出现假名/西里尔字母（OCR 噪声或模型幻觉，
        // 如 "SiGi 廉控状态Aライザーロール"、"移动окрайвер"）
        if (containsKana(s) || containsCyrillic(s)) return true;

        // 键值对残留：表单字段名+值（"商品数量：10"、"业务号: XWICT07311101174"），
        // 全半角冒号都要拦截
        if (s.contains(":") || s.contains("：")) return true;

        // 表头泛化词精确命中（先做全半角归一，避免 "（费用）" 全角差异漏拦）
        if (GENERIC_TABLE_HEADER_WORDS.contains(NameNormalizer.normalize(s))) return true;

        boolean hasCjk = s.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FA5);
        boolean hasAlpha = s.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
        // 无任何中文与字母：纯数值/价格/百分比/日期时间（0、15.27、70%、2024-04-01 11:56:04）
        if (!hasCjk && !hasAlpha) return true;
        // 纯小写 ASCII 标识符：CSS 类名/代码变量/按钮 id（local-btn、sparkley、wah_cv、ess）
        if (s.matches("[a-z0-9_-]+")) return true;
        // 字母前缀 + 长数字：电话、订单号、服务编码（13348612222、XWICT0731101206、ZQ12345）
        if (s.matches("[A-Za-z]{0,8}-?\\d{5,}.*")) return true;
        // 无分隔符的字母数字长编码（1HWZX73120240463884）：≥10 位、含 ≥2 字母 + ≥5 数字。
        // 设备型号均带连字符/空格/中文前缀（华为B866-S2-5E4P5W1、TL-SD256），不受影响
        if (s.matches("[A-Za-z0-9]{10,}")) {
            long letters = s.chars().filter(c -> Character.isLetter(c)).count();
            long digits = s.chars().filter(Character::isDigit).count();
            if (letters >= 2 && digits >= 5) return true;
        }
        // 年份开头的日期串（2024年4月19日、2023.08.19 11:52）
        if (s.matches("\\d{2,4}\\s*年[\\d\\s:月日时分秒./-]*")) return true;
        // 金额/时长带量词（9元、445元/月、103天、7个月）——"元/天/月"是中文字符，
        // 会骗过上面的 hasCjk 检查，需单独识别
        if (s.matches("\\d+([.,]\\d+)?\\s*元(/\\S+)?")) return true;
        if (s.matches("\\d+\\s*(天|个月|月|小时)")) return true;
        // 名称以金额收尾：表格碎片截断出的伪产品名（"头标准包2999元"、"叠加1个摄像头868元"）。
        // 必须带"元"字——设备型号常以数字结尾（华为B671-S2、TP LINK CT4WS-P V2），裸数字结尾不能拦
        if (s.matches(".*\\d+([.,]\\d+)?\\s*元$")) return true;
        // 截断句（一次性费用由、业务受理按钮包含的）与弱模式实体（XX的设备组成、XX信息、产品名称）
        if (s.matches(".*[的了由是与]$")) return true;
        if (s.matches(".*(组成|信息|情况|介绍|描述|说明|名称)$")) return true;
        // 数字 + 单汉字碎片（1主、1从）
        return s.matches("\\d{1,3}[\u4e00-\u9fa5]");
    }

    /** 是否包含日文假名（平假名 3040-309F / 片假名 30A0-30FF） */
    private static boolean containsKana(String s) {
        return s.chars().anyMatch(c -> (c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF));
    }

    /** 是否包含西里尔字母（0400-04FF） */
    private static boolean containsCyrillic(String s) {
        return s.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF);
    }
}
