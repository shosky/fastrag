package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.Map;

@Data
public class SubmitInputRequest {
    /** token(必填) */
    private String token;
    /** 用户提交的输入(对应 user_input.fields 的 values) */
    private Map<String, Object> inputs;
}