package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.Map;

/**
 * 文件处理触发请求（来自前端上传向导，POST /files/{id}/process）。
 *
 * <p>承载上传向导收集的一次性处理配置：
 * 解析策略、内容语言、文本编码、处理优先级、失败重试次数、OCR/ASR 引擎与视频策略、
 * 时间范围裁剪。全部字段持久化到 kb_file.processing_config，
 * 并随 MQ 消息下发，由解析器消费（见 ParseOptions）。
 */
@Data
public class FileProcessRequest {
    /** 处理模式：chunk（默认）/ qa */
    private String processingMode;
    /** 指定解析策略 ID（为空则后端按扩展名自动匹配） */
    private String parseStrategyId;
    /** 内容语言：auto / zh / en / mixed（影响 ASR） */
    private String language;
    /** 文本编码：auto / utf-8 / gbk / shift-jis（TXT/CSV 用） */
    private String encoding;
    /** 处理优先级：low / normal / high（落库，MQ 暂未消费） */
    private String priority;
    /** 失败自动重试次数（默认 0） */
    private Integer retryCount;
    /** 引擎配置：{ocrEngine, asrEngine, videoStrategy, keyframeInterval, vlmModel} */
    private Map<String, Object> engineConfig;
    /** 音视频配置：{speakerDiarize, timeRanges: {fileName: [start, end]}} */
    private Map<String, Object> mediaConfig;
}
