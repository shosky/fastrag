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
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
