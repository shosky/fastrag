package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("mcp_tool") public class McpTool {
    @TableId(type=IdType.AUTO) private Long id;
    private String serviceId,name,description,params;
}
