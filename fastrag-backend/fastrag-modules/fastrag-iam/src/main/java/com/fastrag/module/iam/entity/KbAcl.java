package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_acl")
public class KbAcl {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbId;
    private String userId;
    private String kbRole; // owner / editor / viewer
    private String grantedBy;
    private LocalDateTime grantedAt;
}
