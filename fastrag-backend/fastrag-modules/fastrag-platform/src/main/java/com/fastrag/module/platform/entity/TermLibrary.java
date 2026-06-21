package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("term_library") public class TermLibrary {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,owner;
    private Integer termCount;
    private LocalDateTime createdAt,updatedAt;
}
