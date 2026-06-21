package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("app_template") public class AppTemplate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,type,configSnapshot;
    private Integer usageCount;
}
