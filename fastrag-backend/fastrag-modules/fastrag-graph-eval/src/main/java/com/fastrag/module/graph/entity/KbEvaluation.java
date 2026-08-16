package com.fastrag.module.graph.entity;

/**
 * 知识图谱评测任务实体类，对应数据库表 {@code kb_evaluation}。
 *
 * <p>评测任务是知识图谱质量评估的执行单元。每次评测基于一个基准测试，逐题通过LLM进行问答评测，
 * 记录检索召回率和答案准确率，最终汇总生成综合评测分数。评测任务支持异步执行，可实时查询执行进度。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code id} - 评测任务唯一标识（雪花算法生成）</li>
 *   <li>{@code kbId} - 所属知识库ID</li>
 *   <li>{@code name} - 评测任务名称</li>
 *   <li>{@code benchmark} - 引用的基准测试标识</li>
 *   <li>{@code status} - 任务状态（如 pending/running/completed/failed）</li>
 *   <li>{@code answerModel} - 生成答案使用的LLM模型标识</li>
 *   <li>{@code judgeModel} - 判定答案正确性使用的LLM模型标识</li>
 *   <li>{@code benchmarkCount} - 基准测试题目总数</li>
 *   <li>{@code dataCount} - 待评测数据总数</li>
 *   <li>{@code completedCount} - 已完成评测的数量</li>
 *   <li>{@code duration} - 评测耗时（毫秒）</li>
 *   <li>{@code recallAt1/3/5/10} - 检索召回率（Recall@1, Recall@3, Recall@5, Recall@10）</li>
 *   <li>{@code answerAccuracy} - 答案准确率</li>
 *   <li>{@code overallScore} - 综合评测分数</li>
 *   <li>{@code results} - 非持久化字段，关联查询的评测结果明细列表</li>
 * </ul>
 *
 * <p>与{@link KbEvaluationResult}为一对多关系，评测任务的每道题对应一条评测结果记录。</p>
 *
 * @see KbEvaluationResult
 * @see KbBenchmark
 */
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
