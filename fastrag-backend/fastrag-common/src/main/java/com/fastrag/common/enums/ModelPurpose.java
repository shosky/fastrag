package com.fastrag.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AI模型用途枚举。
 *
 * <p>定义系统中AI模型的不同用途类型：LLM（大语言模型，用于对话和文本生成）、
 * EMBEDDING（Embedding模型，用于文本向量化）、RERANK（Rerank模型，用于检索重排序）、
 * OCR（OCR识别模型，用于图片文字识别）。每个枚举值包含中文标签，
 * 序列化时输出中文标签值。</p>
 */
public enum ModelPurpose {
    LLM("大语言模型"), EMBEDDING("Embedding模型"), RERANK("Rerank模型"), OCR("OCR识别");
    private final String label;
    ModelPurpose(String label) { this.label = label; }
    @JsonValue public String getLabel() { return label; }
}
