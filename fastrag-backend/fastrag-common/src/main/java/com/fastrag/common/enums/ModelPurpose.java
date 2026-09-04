package com.fastrag.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
public enum ModelPurpose {
    LLM("大语言模型"), EMBEDDING("Embedding模型"), RERANK("Rerank模型"), OCR("OCR识别"), TTS("语音合成"), IMAGE_GEN("文生图");
    private final String label;
    ModelPurpose(String label) { this.label = label; }
    @JsonValue public String getLabel() { return label; }
}
