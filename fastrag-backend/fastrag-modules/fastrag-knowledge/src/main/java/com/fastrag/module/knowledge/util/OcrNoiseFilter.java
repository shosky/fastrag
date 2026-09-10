package com.fastrag.module.knowledge.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 嵌入图片 OCR 文本噪声过滤器（行级，保守启发式）。
 *
 * <p>背景：docx/pptx 嵌入截图（CRM 界面、浏览器页面）的 OCR 文本会并入 chunk 正文，
 * 进而污染知识图谱抽取——生产数据中曾抽出"360搜索+百度一下"、"请输入客户联系人"、
 * "Bottom Button"、"账户名称22" 等界面残留实体。本工具在 OCR 结果并入正文前做行级清洗。</p>
 *
 * <p>适用边界：只过滤"嵌入图片的 OCR 文本"（图片是对正文的辅助说明，宁缺勿滥）；
 * 扫描件 PDF 的整页 OCR 是唯一文本来源，不得使用本过滤器。</p>
 *
 * <p>规则（命中即丢弃该行）：</p>
 * <ol>
 *   <li>含日文假名/西里尔字母（跨语言乱码）</li>
 *   <li>含表单占位符（请输入/[输入要求]/输入内容）</li>
 *   <li>纯 UI 按钮/导航词（百度一下、确定、提交、搜索、登录……）</li>
 *   <li>短行（≤8 字符）以"搜索"收尾（搜索框占位：360搜索、高级搜索）</li>
 *   <li>短行（≤6 字符）中文+数字混合（充值110、账户名称22）</li>
 *   <li>纯英文短行（≤20 字母/空格，Bottom Button、Home）——中文域文档截图的英文控件标签</li>
 *   <li>纯数字/日期时间/电话号码行</li>
 * </ol>
 *
 * <p>本类为无状态工具类，仅含静态方法，被 IngestionConsumer 在 OCR 结果入库前调用。</p>
 */
public final class OcrNoiseFilter {

    private OcrNoiseFilter() {}

    /** UI 按钮/导航/搜索框词：整行精确命中（忽略首尾空白）即丢弃 */
    private static final Set<String> UI_CHROME_WORDS = new HashSet<>(Arrays.asList(
            "百度一下", "确定", "取消", "提交", "搜索", "登录", "注册", "首页", "下一页", "上一页",
            "返回", "关闭", "保存", "删除", "编辑", "确认", "重置", "更多", "展开", "收起",
            "下一步", "上一步", "立即办理", "查看更多", "加载中", "菜单"
    ));

    /**
     * 清洗 OCR 文本：逐行过滤噪声行，保留有效行（保持原行序，去除首尾空行）。
     *
     * @param ocrText 原始 OCR 文本（可 null）
     * @return 清洗后的文本；全部为噪声或输入为空时返回空字符串
     */
    public static String sanitize(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) return "";
        StringBuilder kept = new StringBuilder();
        for (String rawLine : ocrText.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (isNoiseLine(line)) continue;
            if (kept.length() > 0) kept.append('\n');
            kept.append(line);
        }
        return kept.toString();
    }

    /** 判定单行 OCR 文本是否为界面噪声 */
    static boolean isNoiseLine(String line) {
        String s = line.trim();
        if (s.isEmpty()) return true;
        // 跨语言乱码（假名/西里尔）：OCR 对截图装饰字符的误识别
        if (containsKana(s) || containsCyrillic(s)) return true;
        // 表单占位符
        if (s.contains("请输入") || s.contains("[输入要求]") || s.contains("输入内容")) return true;
        // 纯 UI 按钮/导航词
        if (UI_CHROME_WORDS.contains(s)) return true;
        // 搜索框占位（360搜索、高级搜索）
        if (s.length() <= 8 && s.endsWith("搜索")) return true;
        // 短行中文+数字混合（充值110、账户名称22）
        boolean hasCjk = s.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FA5);
        boolean hasDigit = s.chars().anyMatch(Character::isDigit);
        if (s.length() <= 6 && hasCjk && hasDigit) return true;
        // 纯英文短行：中文域文档截图的英文控件标签（Bottom Button、Home）
        if (s.length() <= 20 && s.matches("[A-Za-z][A-Za-z ]*")) return true;
        // 纯数字/日期时间/电话号码
        return s.matches("[\\d\\s:./\\-年月日时分秒]+");
    }

    private static boolean containsKana(String s) {
        return s.chars().anyMatch(c -> (c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF));
    }

    private static boolean containsCyrillic(String s) {
        return s.chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF);
    }
}
