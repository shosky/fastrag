package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_retrieval_log") public class KbRetrievalLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String kbId,query,userId;
    private Integer hitCount,latencyMs;
    private Double topScore;
    private Boolean hasResult;
    private LocalDateTime createdAt;
}
