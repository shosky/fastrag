package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_similar_question") public class KbSimilarQuestion {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,standardQuestionId,question;
    private Double similarity;
    private Integer hitCount,enabled;
    private LocalDateTime createdAt,updatedAt;
}
