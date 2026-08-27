package com.fastrag.module.knowledge.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库文件信息DTO。
 *
 * <p>表示知识库中一个文件的完整信息，包含文件ID、名称、分类、扩展名、下载URL、
 * 处理状态、处理阶段、解析策略、所属文件夹、处理模式、大小、进度、页数、分块数
 * 以及时间戳等。用于文件列表展示和详情查询接口的返回。</p>
 */
@Data
public class FileDto {
    private String id, name, category, extension, url, status, stage;
    private String parseStrategyId, parseStrategyName, folderId, processingMode;
    /** 当前绑定策略的高级配置（含文件级专属策略；分片策略设置对话框据此回填参数） */
    private Map<String, Object> parseStrategyAdvanced;
    private Integer enableGraphBuild; // 是否构建知识图谱
    private Long size;
    private Integer progress, duration, pages, chunkCount;
    private LocalDateTime deletedAt, createdAt, updatedAt;

    // ===== 元数据摘要（分册四：列表列渲染 / 检索过滤展示用，完整字段走 GET .../metadata） =====
    private String region;          // 地域（可多值 JSON 数组串）
    private LocalDate publishDate;  // 发文日期
    private String docLevel;        // 发文层级
    private String metadataStatus;  // none/partial/full/revised
    private String metadataSource;  // manual/auto/mixed
    private List<String> tags;      // 文件标签名列表（用于列表标签列展示）
}
