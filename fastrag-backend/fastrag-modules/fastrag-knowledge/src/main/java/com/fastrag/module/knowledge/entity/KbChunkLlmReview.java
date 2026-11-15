package com.fastrag.module.knowledge.entity;

/**
 * LLM 分片边界审查明细（对应数据库表 kb_chunk_llm_review）。
 *
 * <p>记录 structure_aware 分片经 LLM 边界审查后的逐片调整说明：
 * 审查模型、是否被调整（merge/split）、调整原因。检索主链不消费本表，
 * 供运营排查分片质量问题与事后批量补审定位。</p>
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chunk_llm_review")
public class KbChunkLlmReview {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 分片 ID（{fileId}_chunk_{n}） */
    private String chunkId;
    private String kbId;
    private String fileId;
    /** 审查用 LLM 模型 */
    private String model;
    /** 1=边界被调整（merge/split）0=审查后保持原样 */
    private Integer adjusted;
    /** LLM 边界调整说明 */
    private String note;
    /** 审查 prompt 版本 */
    private String promptVersion;
    private LocalDateTime createdAt;
}
