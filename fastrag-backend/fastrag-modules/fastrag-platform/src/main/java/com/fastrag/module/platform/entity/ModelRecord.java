package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import lombok.Data;
@Data @TableName("model") public class ModelRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,code,purpose,brand,apiUrl,apiKeyRef,status;
    // 真实访问密钥（api_key 列）：仅后端调用上游时使用，@JsonIgnore 保证列表/详情接口不回显
    @JsonIgnore private String apiKey;
    // 模型阈值设置（JSON：如 {"scoreThreshold":0.6,"rerankThreshold":0.3,"maxTokens":2048}）
    private String threshold;
}
