package com.fastrag.common.util;

/**
 * 输出预算工具（I1，finding gap-1）：rune（code point）安全的文本截断。
 *
 * <p>背景：工具输出/沙箱输出原先直接 {@code substring(0, maxLen)}，在 emoji 等
 * 增补码点（surrogate pair，1 个码点 = 2 个 char）中间切割会产出孤立代理项，
 * 破坏后续 JSON 序列化与 LLM 上下文。本工具保证：截断边界永不落在代理对中间。</p>
 *
 * <p>消费方：AgentEngine.truncate（工具输出 5000 字符）、SandboxExecutionService.truncate
 * （沙箱输出 256K）、HttpToolExecutor（HTTP 响应体 2M 上限）。</p>
 */
public final class OutputBudget {

    /** 引擎侧默认截断标记（保持既有语义）。 */
    public static final String DEFAULT_TRUNCATED_MARKER = "...(truncated)";

    private OutputBudget() {
    }

    /**
     * 按字符预算截断，保证不拆散增补码点。
     *
     * <p>实现：预算边界落在低位代理上时，说明前一个 char 是被拆散的高位代理，
     * 用 {@code offsetByCodePoints} 按码点回退一个完整码点再切。</p>
     *
     * @param text     原文（可为 null）
     * @param maxChars 最大字符数（UTF-16 char，非码点）
     * @param marker   截断标记（追加在被截断的输出尾部）
     * @return 原文（未超预算）或截断后前缀 + 标记；null 输入返回空串
     */
    public static String truncate(String text, int maxChars, String marker) {
        if (text == null) {
            return "";
        }
        if (maxChars < 0 || text.length() <= maxChars) {
            return text;
        }
        int cut = maxChars;
        if (cut > 0 && Character.isLowSurrogate(text.charAt(cut))) {
            // 边界落在代理对中间：回退一个完整码点
            cut = text.offsetByCodePoints(0, text.codePointCount(0, cut) - 1);
        }
        if (cut <= 0) {
            return marker;
        }
        return text.substring(0, cut) + marker;
    }

    /**
     * 头尾保留截断（I3）：超预算时保留头部（70%）与尾部（30%），中间以
     * {@code ...(中间 N 字符省略)...} 标记整体省略——错误信息/统计行常在输出尾部，
     * 掐尾式截断会把最有诊断价值的部分丢掉。
     *
     * <p>头尾边界同样保证不拆散增补码点；省略计数按 code point 统计。</p>
     *
     * @param text     原文（可为 null）
     * @param maxChars 输出总字符预算（不含省略标记本身）
     * @return 原文（未超预算）或 头 + 省略标记 + 尾；null 输入返回空串
     */
    public static String truncateMiddle(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int headChars = (int) (maxChars * 0.7);
        int tailChars = maxChars - headChars;

        int headCut = headChars;
        if (headCut > 0 && headCut < text.length() && Character.isLowSurrogate(text.charAt(headCut))) {
            headCut = text.offsetByCodePoints(0, text.codePointCount(0, headCut) - 1);
        }
        int tailStart = text.length() - tailChars;
        if (tailStart > 0 && tailStart < text.length() && Character.isLowSurrogate(text.charAt(tailStart))) {
            // 起点落在代理对中间（高位在界外）：整个码点丢弃，从低位之后开始
            tailStart++;
        }
        if (tailStart < headCut) {
            tailStart = headCut;
        }
        int omitted = Character.codePointCount(text, headCut, tailStart);
        String head = headCut > 0 ? text.substring(0, headCut) : "";
        String tail = tailStart < text.length() ? text.substring(tailStart) : "";
        return head + "\n...(中间 " + omitted + " 字符省略)...\n" + tail;
    }
}
