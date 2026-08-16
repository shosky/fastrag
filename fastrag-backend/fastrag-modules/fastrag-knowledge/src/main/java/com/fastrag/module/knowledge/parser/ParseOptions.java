package com.fastrag.module.knowledge.parser;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 单次文件解析的处理选项（来自前端上传向导，随 process 请求下发）。
 *
 * <p>与 {@code kb_parse_strategy.advanced}（策略级默认值）的关系：
 * 本选项为「上传时的一次性覆盖」，解析时优先于策略配置；为 null 的字段
 * 继续使用策略/系统默认值。通过 MQ 消息的 {@code processingConfig} 传递，
 * 由 IngestionConsumer 构造后传入 {@link DocumentParser#parse}。
 */
@Data
@Builder
public class ParseOptions {
    /** OCR 引擎代码（deepseek / paddle），null=系统默认引擎 */
    private String ocrEngine;
    /** ASR 引擎代码（funasr / whisper），null=系统默认引擎 */
    private String asrEngine;
    /** 内容语言（auto / zh / en / mixed），影响 ASR，null=auto */
    private String language;
    /** 文本编码（auto / utf-8 / gbk / shift-jis），TXT/CSV 文本解析用，null=auto（自动检测） */
    private String encoding;
    /** 视频策略：keyframe_asr（关键帧+ASR，默认）/ asr_only（仅 ASR）/ uniform_sample（仅关键帧均匀采样） */
    private String videoStrategy;
    /** 关键帧采样间隔（秒），覆盖策略 advanced.keyframeIntervalSeconds */
    private Integer keyframeInterval;
    /** 时间范围裁剪（秒）：fileName → [start, end]，仅裁剪该范围内的音视频内容 */
    private Map<String, double[]> timeRanges;
    /** 原文件名（timeRanges 按文件名匹配时使用） */
    private String fileName;
}
