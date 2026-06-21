package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("tool_http_config") public class ToolHttpConfig {
    @TableId(type=IdType.ASSIGN_ID) private String toolId;
    private String method,url,authType,params,headers,bodyType,body;
}
