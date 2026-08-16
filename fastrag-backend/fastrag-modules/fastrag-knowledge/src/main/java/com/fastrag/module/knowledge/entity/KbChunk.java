package com.fastrag.module.knowledge.entity;
/**
 * 知识块实体类，对应数据库表 kb_chunk，是文档解析分块后的核心持久化模型。
 *
 * <p>核心职责：
 * 存储文档经解析和分块后的文本片段，每个知识块关联一个文件（fileId）和知识库（kbId），
 * 是向量检索和知识问答的基本检索单元。支持 PDF 页感知分块（pageNumber、pageRange、imageKeys）
 * 和结构感知分块（title、headingPath），同时记录音视频的时间戳信息。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 主键，格式为 {fileId}_chunk_{index}（INPUT 类型，非自动生成）</li>
 *   <li>kbId / fileId — 所属知识库和文件 ID</li>
 *   <li>chunkIndex — 分块序号，标识文档中的位置</li>
 *   <li>content — 分块文本内容</li>
 *   <li>embeddingId — Milvus 向量数据库中的向量 ID</li>
 *   <li>vectorStored — 是否已写入向量库（0=未写入，1=已写入）</li>
 *   <li>startTime / endTime — 音视频分块的起止时间戳（秒）</li>
 *   <li>pageNumber / pageRange — PDF 页码感知：所属页码和页码范围（如 "3-4"）</li>
 *   <li>imageKeys — JSON 数组，存储该分块关联的图片对象键列表</li>
 *   <li>chunkType — 分块类型："text"（文本）或 "image"（图片占位）</li>
 *   <li>title / headingPath — 结构感知分块：所属最近标题和层级路径（如 "第一章 > 1.1 背景"）</li>
 *   <li>graphIndexed — 是否已完成知识图谱实体提取（0=未提取，1=已提取）</li>
 *   <li>extractionResult — 图谱提取结果缓存，JSON 格式</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = IdType.INPUT)
    private String id; // format: {fileId}_chunk_{index} 或 {fileId}_parent_{n}
    private String kbId;
    private String fileId;
    private String fileName;
    private String parentId;          // 父子分片：子分片指向父分片 ID，父分片本身为 null
    private Integer chunkIndex;
    private String content;
    private String embeddingId;
    private Integer vectorStored;
    private Double startTime;
    private Double endTime;

    // PDF 页感知分块
    private Integer pageNumber;       // 所属页码
    private String pageRange;         // 页码范围 "3-4"
    private String imageKeys;         // JSON 数组 ["page_1_img_0.png"]
    private String chunkType;         // "text" | "image" | "parent"，默认 "text"

    // 结构感知分片（新增）
    private String title;               // 所属最近标题
    private String headingPath;         // 层级路径 "第一章 > 1.1 背景"

    // 知识图谱相关
    /** 是否已完成知识图谱提取（0=未提取，1=已提取） */
    private Integer graphIndexed;
    /** 图谱提取结果缓存（JSON 格式） */
    private String extractionResult;
}
