package com.fastrag.module.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单行导入结果。
 *
 * <p>记录 Excel 中每一行数据的导入状态，成功行附带解析后的字段值供服务层写入数据库。</p>
 */
@Data
@AllArgsConstructor
public class ImportRowResult {
    /** Excel 行号（从 2 开始，第 1 行为表头） */
    private int rowNumber;
    /** 问题文本（截断用于展示） */
    private String question;
    /** 状态：success / skipped / failed */
    private String status;
    /** 原因说明（失败或跳过时使用） */
    private String reason;

    // ---------- 解析后的字段值（仅 status=success 时填充） ----------
    /** 答案文本 */
    private String answer;
    /** 分类 */
    private String category;
    /** 关键词（原始字符串） */
    private String keywords;
    /** 优先级 */
    private Integer priority;
    /** 状态值 */
    private String qaStatus;
    /** 归一化后的问题文本（用于重复检测 key） */
    private String normalizedQuestion;

    // 便利构造：失败/跳过
    public static ImportRowResult failed(int rowNumber, String question, String reason) {
        return new ImportRowResult(rowNumber, question, "failed", reason,
                null, null, null, null, null, null);
    }

    public static ImportRowResult skipped(int rowNumber, String question, String reason) {
        return new ImportRowResult(rowNumber, question, "skipped", reason,
                null, null, null, null, null, null);
    }

    public static ImportRowResult success(int rowNumber, String question, String answer,
                                          String category, String keywords,
                                          Integer priority, String qaStatus,
                                          String normalizedQuestion) {
        return new ImportRowResult(rowNumber, question, "success", null,
                answer, category, keywords, priority, qaStatus, normalizedQuestion);
    }
}
