package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permKey;
    private String name;
    private String type; // menu / action
    private String group; // menu / kb / app / workflow / review / admin
    private String parentKey;
    private String description;
}
