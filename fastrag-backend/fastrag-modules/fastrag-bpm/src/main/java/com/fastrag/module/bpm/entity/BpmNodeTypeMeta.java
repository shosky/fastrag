package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("bpm_node_type_meta") public class BpmNodeTypeMeta {
    @TableId(type=IdType.ASSIGN_ID) private String type;
    private String label,icon,color,category,description,configSchema,defaultConfig;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}