package com.fastrag.module.graph.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("kb_evaluation_result")
public class KbEvaluationResult {
    @TableId(type = IdType.AUTO) private Long id;
    private String evaluationId, question, generatedAnswer, retrievalMetrics, judgeReason;
    private Integer isCorrect;
}
