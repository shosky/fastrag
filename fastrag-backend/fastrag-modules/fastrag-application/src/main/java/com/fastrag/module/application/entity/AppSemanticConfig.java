package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 语义理解/语义定制配置（对话知识）：意图模式 + 定制同义词 */
@Data @TableName("app_semantic_config") public class AppSemanticConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId;
    // 意图模式：JSON 数组 [{"intent":"查询订单","patterns":["查订单","订单进度"],"threshold":0.6}]
    private String intentPatterns;
    // 定制同义词：JSON 数组 [{"standard":"退款","synonyms":["退钱","退货退款"]}]
    private String customSynonyms;
    private LocalDateTime createdAt,updatedAt;
}
