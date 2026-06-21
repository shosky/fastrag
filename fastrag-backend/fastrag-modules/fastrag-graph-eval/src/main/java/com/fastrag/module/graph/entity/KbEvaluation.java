package com.fastrag.module.graph.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@TableName("kb_evaluation")
public class KbEvaluation {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, name, benchmark, status, runId, answerModel, judgeModel;
    private Integer benchmarkCount, dataCount, completedCount;
    private Long duration;
    private BigDecimal recallAt1, recallAt3, recallAt5, recallAt10, answerAccuracy, overallScore;
    private LocalDateTime createdAt;
}
