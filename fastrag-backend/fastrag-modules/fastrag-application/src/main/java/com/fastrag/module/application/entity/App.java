package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app") public class App {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,type,icon,tags,status,owner;
    private LocalDateTime createdAt,updatedAt;
}
