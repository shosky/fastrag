package com.fastrag.module.graph.entity;

/**
 * 评测结果明细实体类，对应数据库表 {@code kb_evaluation_result}。
 *
 * <p>记录评测任务中每道题的详细评测结果，包括LLM生成的答案、检索指标和判定结果。
 * 每条记录对应基准测试中的一道题目在单次评测中的执行结果。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code id} - 结果自增主键</li>
 *   <li>{@code evaluationId} - 所属评测任务ID，关联 {@link KbEvaluation}</li>
 *   <li>{@code question} - 测试题目文本</li>
 *   <li>{@code generatedAnswer} - LLM生成的答案</li>
 *   <li>{@code retrievalMetrics} - 检索指标数据（JSON格式），记录检索过程的具体指标</li>
 *   <li>{@code judgeReason} - 判定理由，LLM评判答案正确性的推理过程</li>
 *   <li>{@code isCorrect} - 答案是否正确（0/1），由判定模型给出</li>
 *   <li>{@code recallAt1/3/5/10} - 结构化检索召回率指标，分别表示Top-1/3/5/10的召回率</li>
 * </ul>
 *
 * @see KbEvaluation
 */
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
