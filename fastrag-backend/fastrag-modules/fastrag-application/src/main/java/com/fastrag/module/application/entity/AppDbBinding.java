package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_db_binding") public class AppDbBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,dbId,alias,allowedTables;
    private Integer enabled; private LocalDateTime createdAt;
}
