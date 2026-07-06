package com.fastrag.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "agent", autoResultMap = true)
public class Agent {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String slug;

    private String name;

    private String backendId;

    private String description;

    private String icon;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> pics;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> shareConfig;

    private Boolean isDefault;

    private Boolean isSubagent;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
