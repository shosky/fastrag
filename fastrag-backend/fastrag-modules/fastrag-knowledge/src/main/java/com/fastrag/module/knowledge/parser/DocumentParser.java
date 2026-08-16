package com.fastrag.module.knowledge.parser;

import com.fastrag.module.knowledge.entity.KbParseStrategy;

import java.io.InputStream;

/**
 * 文档解析器接口。
 *
 * <p>定义所有文档解析的统一入口，将上传的文件字节流解析为结构化的 {@link ParseResult}。
 * 解析结果包含文本内容、段落、图片、音频视频分段等信息，供后续分块和向量化使用。
 * 由 {@code DocumentParserImpl} 实现，支持按文件扩展名路由到不同解析策略。</p>
 *
 * <p>{@link #parse(InputStream, String, String)} 使用策略/系统默认配置；
 * {@link #parse(InputStream, String, String, ParseOptions)} 支持上传向导传入的一次性
 * 处理选项（OCR/ASR 引擎、语言、编码、视频策略、关键帧间隔、时间范围裁剪）。</p>
 */
public interface DocumentParser {
    ParseResult parse(InputStream fileStream, String extension, String strategyId);

    ParseResult parse(InputStream fileStream, String extension, String strategyId, ParseOptions options);

    /**
     * 按显式策略对象解析（预览场景：临时构造的策略对象，未落库，含自定义解析方式与 advanced 配置）。
     */
    ParseResult parse(InputStream fileStream, String extension, KbParseStrategy strategy, ParseOptions options);
}
