package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_team_member")
public class SysTeamMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String teamId;
    private String userId;
}
