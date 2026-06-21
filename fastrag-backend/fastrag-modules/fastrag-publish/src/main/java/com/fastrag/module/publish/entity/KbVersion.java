package com.fastrag.module.publish.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_version")
public class KbVersion {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, name, description, tags, publishStatus, changeSummary, createdBy;
    private Integer version, fileCount, chunkCount;
    private LocalDateTime createdAt;
}
