package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("kb_folder")
public class KbFolder {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String name;
    private String parentId;
    private Integer sort;
}
