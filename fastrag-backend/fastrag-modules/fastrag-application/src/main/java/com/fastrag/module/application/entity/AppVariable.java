package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_variable") public class AppVariable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,varKey,varType,defaultValue,description,createdBy;
    private LocalDateTime createdAt;
}
