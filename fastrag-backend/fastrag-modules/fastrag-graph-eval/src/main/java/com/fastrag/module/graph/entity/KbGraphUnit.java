package com.fastrag.module.graph.entity;

/**
 * 图谱抽取单元实体，对应数据库表 {@code kb_graph_unit}。
 *
 * <p>v1.2 架构：抽取单元与检索分片解耦（LightRAG 同款思路）——图谱抽取不再逐 500 字分片进行，
 * 而是把文件全部分片按 chunkIndex 顺序重组为 ~2000 字的"抽取单元"，单次 LLM 调用抽取一个单元
 * 的实体与关系。语义完整的章节（如"营销六步法"标题+六个步骤）天然落在同一单元内，
 * 无需任何跨分片补丁机制。</p>
 *
 * <p>本表是单元级缓存与 replay 载体：content_hash 相同的单元重建时直接跳过（零 LLM 成本），
 * replay 模式重放持久化的 extraction_result。</p>
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_graph_unit")
public class KbGraphUnit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbId;
    private String fileId;
    /** 单元在文件内的序号（按 chunkIndex 顺序重组后的下标） */
    private Integer unitIndex;
    /** 单元内容 SHA-256 截断哈希——内容不变则缓存命中，跳过 LLM */
    private String contentHash;
    /** 组成该单元的分片 ID JSON 数组（证据归属与状态回写的依据） */
    private String chunkIds;
    /** 抽取结果 JSON（ExtractionResult 结构），replay 零 LLM 重放 */
    private String extractionResult;
    /** 0=待抽取 1=已完成 2=失败 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
