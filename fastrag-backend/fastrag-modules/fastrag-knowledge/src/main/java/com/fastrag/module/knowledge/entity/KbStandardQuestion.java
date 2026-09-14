package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_standard_question") public class KbStandardQuestion {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,category,standardQuestion,answer;
    private Integer hitCount,enabled;
    private LocalDateTime createdAt,updatedAt;
}
