package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("model") public class ModelRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,code,purpose,brand,apiUrl,apiKeyRef,status;
    // 模型阈值设置（JSON：如 {"scoreThreshold":0.6,"rerankThreshold":0.3,"maxTokens":2048}）
    private String threshold;
}
