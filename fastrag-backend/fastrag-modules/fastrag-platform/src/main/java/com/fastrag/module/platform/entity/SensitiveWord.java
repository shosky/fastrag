package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sensitive_word") public class SensitiveWord {
    @TableId(type=IdType.AUTO) private Long id;
    private String word,category,level,replacement;
    private Integer enabled;
    private LocalDateTime createdAt;
}
