package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.Map;

@Data
public class TestCaseRequest {
    private String name;
    private Map<String, Object> inputs;
    private String expectedOutput;
}