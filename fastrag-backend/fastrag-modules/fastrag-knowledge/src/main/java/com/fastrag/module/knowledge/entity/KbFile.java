package com.fastrag.module.knowledge.entity;
/**
 * 知识库文件实体类，对应数据库表 kb_file，是文档摄入流程的核心数据模型。
 *
 * <p>核心职责：
 * 表示上传到知识库中的一个文件，记录文件元信息、处理状态、解析策略关联、
 * 分块统计和存储位置等。文件经 IngestionConsumer 异步处理后从 pending 变为
 * completed 或 failed，处理过程包含下载、解析、分块、OCR、向量存储等阶段。
 * 支持软删除（deletedAt）和回收站功能。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>name — 文件名</li>
 *   <li>category — 文件类别：document（文档）/ image（图片）/ audio（音频）/ video（视频）</li>
 *   <li>extension — 文件扩展名（如 pdf、docx、mp3 等）</li>
 *   <li>size — 文件大小（字节）</li>
 *   <li>objectKey — MinIO 对象存储中的文件路径键</li>
 *   <li>status — 处理状态：pending（待处理）/ processing（处理中）/ completed（已完成）/ failed（失败）</li>
 *   <li>progress — 处理进度百分比</li>
 *   <li>stage — 当前处理阶段描述</li>
 *   <li>duration — 音视频时长（秒）</li>
 *   <li>pages — PDF/DOCX 文档页数</li>
 *   <li>parseStrategyId / parseStrategyName — 关联的解析策略 ID 和名称</li>
 *   <li>chunkCount — 已生成的分块数量</li>
 *   <li>processingMode — 处理模式：chunk（分块入库）或 qa（问答对提取）</li>
 *   <li>enableGraphBuild — 是否构建知识图谱（0=否，1=是）</li>
 *   <li>folderId — 所属文件夹 ID</li>
 *   <li>viewCount — 文件查看次数</li>
 *   <li>deletedAt — 软删除时间戳，非空表示已移入回收站</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("kb_file")
public class KbFile {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String name;
    private String category; // document / image / audio / video
    private String extension;
    private Long size;
    private String objectKey;
    private String status; // pending / processing / completed / failed
    private Integer progress;
    private String stage;
    private Integer duration;
    private Integer pages;
    private String parseStrategyId;
    private String parseStrategyName;
    private Integer chunkCount;
    private String processingMode; // chunk 或 qa
    private String processingConfig; // JSON：上传向导的处理配置（引擎/语言/编码/优先级/重试/媒体），重试与重新分片时复用
    private Integer enableGraphBuild; // 该文件是否构建知识图谱，默认 0
    private String folderId;
    private Long viewCount;
    // ===== 业务元数据（文档级，分册四 rag-file-metadata-management.md） =====
    private String region;         // 地域（省/市，多值 JSON 数组串，如 ["湖南省","长沙"]）
    private LocalDate publishDate; // 发文日期
    private String docLevel;       // 发文层级: national / provincial / municipal / county / unknown
    private String issuer;         // 发文机关
    private String docNumber;      // 文号（如 发改价格〔2024〕123号）
    private String metadataStatus; // 元数据状态: none/partial/full/revised（未抽取/部分/完整/人工校订）
    private String customAttrs;    // 自定义属性取值（JSON KV，schema 见 kb.custom_attr_schema）
    private String metadataSource; // 填充来源: manual/auto/mixed
    private LocalDateTime deletedAt;

    /**
     * Markdown 全文对象键（运行时由 IngestionConsumer 在解析完成后写入 MinIO，
     * 约定路径 {kbId}/{fileId}/parsed.md）。
     *
     * <p>本字段为 transient，未映射到 kb_file 表（不依赖 DDL 迁移，见
     * docs/design/parsed-markdown.md ADR-1）。前端/接口通过 MinIO 路径约定直接定位对象，
     * 本字段仅作 Java 层可读性说明，供后续若加列时一行去掉 transient 即可。</p>
     */
    private transient String markdownObjectKey;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
