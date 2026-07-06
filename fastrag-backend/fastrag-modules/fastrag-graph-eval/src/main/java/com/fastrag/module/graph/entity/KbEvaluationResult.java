package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("kb_evaluation_result")
public class KbEvaluationResult {
    @TableId(type = IdType.AUTO) private Long id;
    private String evaluationId, question, generatedAnswer, retrievalMetrics, judgeReason;
    private Integer isCorrect;

    /** 结构化检索指标：Recall@K */
    @TableField("recall_at_1") private BigDecimal recallAt1;
    @TableField("recall_at_3") private BigDecimal recallAt3;
    @TableField("recall_at_5") private BigDecimal recallAt5;
    @TableField("recall_at_10") private BigDecimal recallAt10;
}
