package com.fastrag.module.publish.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_review_task")
public class KbReviewTask {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, kbName, versionId, applicant, reviewer, status, comment;
    private Integer version;
    private LocalDateTime createdAt, reviewedAt;
}
