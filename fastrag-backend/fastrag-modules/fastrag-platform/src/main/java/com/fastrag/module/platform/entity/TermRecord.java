package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("term_record") public class TermRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String libraryId,term,alias,definition,category;
    private LocalDateTime createdAt;
}
