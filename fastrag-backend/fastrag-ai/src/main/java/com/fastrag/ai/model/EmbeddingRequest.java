package com.fastrag.ai.model;

import lombok.Data;
import java.util.List;

@Data
public class EmbeddingRequest {
    private String model;
    private List<String> input;
}
