package com.fastrag.module.knowledge.entity;
/**
 * 问答对提炼任务账本实体，对应数据库表 kb_qa_generation_task（qa-generation 特性）。
 *
 * <p>核心职责：
 * 每文件一行的提炼任务状态账本（upsert 覆盖语义，新任务整行覆盖旧记录），
 * 记录任务四态（running/success/partial/failed）与分片级统计，供前端轮询进度、
 * 承载文件级持久化互斥（running 且心跳新鲜 = 任务进行中）与陈旧恢复判定
 * （updated_at 即心跳，超 qa.generation.stale-minutes 无心跳的 running 行清扫为 failed）。
 *
 * <p>与 kb_qa_pair 无引用关系——本表是状态账本非产出索引，产出即 QA 对本身（经 file_id 间接关联）。
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_qa_generation_task")
public class KbQaGenerationTask {
    /** 所属文件 ID（全局雪花 id）；主键即 upsert 语义的锚 */
    @TableId(type = IdType.INPUT)
    private String fileId;
    private String kbId;
    /** idle / running / success / partial / failed */
    private String status;
    /** 受理时定格的父分片总数 */
    private Integer totalChunks;
    /** 成功分片数（含「无可答题材料出零条」的正常完结） */
    private Integer succeededChunks;
    /** 失败分片数 + 闸门拦截产出数（合并记账） */
    private Integer failedChunks;
    /** 参数快照 JSON：{count, customInstruction, mode, model}——执行期唯一依据 */
    private String config;
    /** failed/partial 摘要 */
    private String error;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 心跳：每分片完结由代码显式刷新，兼作陈旧判定依据 */
    private LocalDateTime updatedAt;
}
