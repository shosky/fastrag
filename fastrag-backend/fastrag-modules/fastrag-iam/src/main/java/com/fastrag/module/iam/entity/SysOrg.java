package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_org")
public class SysOrg {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String alias;
    private String parentId;
    private Integer level;
    private Integer sort;
}
