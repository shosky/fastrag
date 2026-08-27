package com.fastrag.module.knowledge.model;

import lombok.Data;
import java.util.List;

/**
 * 问答对批量导入结果。
 *
 * <p>汇总 Excel 导入的整体统计信息和逐行明细，返回给前端用于结果展示。</p>
 */
@Data
public class QaImportResult {
    /** 总数据行数（不含表头） */
    private int totalRows;
    /** 新增成功数 */
    private int successCount;
    /** 跳过数（重复项，未覆盖模式下） */
    private int skipCount;
    /** 失败数（数据格式错误等） */
    private int failCount;
    /** 逐行结果明细 */
    private List<ImportRowResult> details;

    public QaImportResult() {
        this.totalRows = 0;
        this.successCount = 0;
        this.skipCount = 0;
        this.failCount = 0;
        this.details = new java.util.ArrayList<>();
    }
}
