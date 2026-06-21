package com.fastrag.common.enums;
import com.fasterxml.jackson.annotation.JsonValue;
public enum ModelPurpose {
    LLM("大语言模型"), EMBEDDING("Embedding模型"), RERANK("Rerank模型"), OCR("OCR识别");
    private final String label;
    ModelPurpose(String label) { this.label = label; }
    @JsonValue public String getLabel() { return label; }
}
