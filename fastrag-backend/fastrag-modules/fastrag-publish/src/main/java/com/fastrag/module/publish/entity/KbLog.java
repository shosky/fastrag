package com.fastrag.module.publish.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_log")
public class KbLog {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, category, action, target, detail, operator, status, extra;
    private LocalDateTime timestamp;
}
