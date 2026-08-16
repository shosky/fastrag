package com.fastrag.module.knowledge.chunking;

/**
 * 文本分块数据模型，用于承载文档经分块处理后产生的单个切片信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>携带分块唯一标识（id）和序号（index）</li>
 *   <li>保存分块正文内容（content）</li>
 *   <li>支持音视频场景的时间戳信息（startTime / endTime）</li>
 *   <li>支持 PDF 页感知分块，记录所属页码（pageNumber）和页码范围（pageRange）</li>
 *   <li>支持关联 MinIO 图片 key 列表（imageKeys），用于图文混排切片</li>
 *   <li>区分分块类型（chunkType）：text（纯文本）、image（图片）、table（表格）、code（代码）</li>
 *   <li>支持结构感知分块，携带所属最近标题（title）和层级路径（headingPath）</li>
 * </ul>
 *
 * <p>该类通过 Lombok @Builder 构建，是 ChunkingService 分块输出的核心数据结构，
 * 最终会被持久化到 kb_chunk 表中用于向量检索。
 */
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChunkData {
    private String id;
    private int index;
    private String content;
    private Double startTime;
    private Double endTime;

    /** 父子分片：子分片指向其所属父分片的 id（存储层聚合后回填），父分片本身为 null */
    private String parentId;

    // PDF 页感知分块
    private Integer pageNumber;       // 所属页码 (1-based)
    private String pageRange;         // 页码范围 "3-4"
    private List<String> imageKeys;   // 关联的 MinIO 图片 key
    private String chunkType;         // "text" | "image" | "table" | "code"，默认 "text"
    private String title;             // 所属最近标题（新增）
    private String headingPath;       // 层级路径，如 "第一章 > 1.1 背景"（新增）
}
