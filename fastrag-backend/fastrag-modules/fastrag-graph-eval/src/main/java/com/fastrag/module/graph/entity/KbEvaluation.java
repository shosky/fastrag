package com.fastrag.module.graph.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("kb_evaluation")
public class KbEvaluation {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, name, benchmark, status, answerModel, judgeModel;
    /** @deprecated 该字段已废弃，不再使用 */
    @Deprecated
    private String runId;
    private Integer benchmarkCount, dataCount, completedCount;
    private Long duration;
    @TableField("recall_at_1") private BigDecimal recallAt1;
    @TableField("recall_at_3") private BigDecimal recallAt3;
    @TableField("recall_at_5") private BigDecimal recallAt5;
    @TableField("recall_at_10") private BigDecimal recallAt10;
    private BigDecimal answerAccuracy, overallScore;
    private LocalDateTime createdAt;
    /** 子查询：每道题的评估结果（非持久化字段） */
    @TableField(exist = false) private List<KbEvaluationResult> results;
}
