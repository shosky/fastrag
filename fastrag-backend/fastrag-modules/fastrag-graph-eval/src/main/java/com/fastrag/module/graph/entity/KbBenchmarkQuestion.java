package com.fastrag.module.graph.entity;

/**
 * 基准测试题目实体类，对应数据库表 {@code kb_benchmark_question}。
 *
 * <p>存储基准测试中的每道测试题目及其标准答案。每道题目基于知识图谱中的三元组或实体信息生成，
 * 包含问题文本、标准检索chunk（用于评测检索召回率）和标准答案（用于评测答案准确率）。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code id} - 题目自增主键</li>
 *   <li>{@code benchmarkId} - 所属基准测试ID，关联 {@link KbBenchmark}</li>
 *   <li>{@code question} - 题目文本，基于图谱内容的问题</li>
 *   <li>{@code goldChunks} - 标准检索chunk标识（JSON格式），用于计算Recall@K等检索指标</li>
 *   <li>{@code goldAnswer} - 标准答案文本，用于对比LLM生成答案的准确率</li>
 *   <li>{@code questionIndex} - 题目序号，标识题目在基准测试中的顺序</li>
 *   <li>{@code crossBoundary} - 跨界切断标志（0/1）：1=标准答案横跨两个相邻 chunk，
 *       专门用于验证"切分边界切断后，检索增强手段能否把内容完整召回"</li>
 * </ul>
 *
 * <p>在评测执行时，系统会逐题查询LLM获取生成答案，然后与goldAnswer对比计算准确率。</p>
 *
 * @see KbBenchmark
 * @see KbEvaluationResult
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("kb_benchmark_question")
public class KbBenchmarkQuestion {
    @TableId(type = IdType.AUTO) private Long id;
    private String benchmarkId, question, goldChunks, goldAnswer;
    private Integer questionIndex;
    /** 跨界切断标志：1 = 答案跨多个相邻 chunk（评测召回完整度专用） */
    private Integer crossBoundary;
}
