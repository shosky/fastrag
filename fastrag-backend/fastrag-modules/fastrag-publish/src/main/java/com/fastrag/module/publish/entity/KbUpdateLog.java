package com.fastrag.module.publish.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_update_log")
public class KbUpdateLog {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, updateType, target, detail, oldValue, newValue, operator;
    private LocalDateTime timestamp;
}
