package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_publish_record") public class AppPublishRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,status,scopeType,scopeValue,configSnapshot,operator;
    private Integer version; private LocalDateTime publishedAt,createdAt;
}
