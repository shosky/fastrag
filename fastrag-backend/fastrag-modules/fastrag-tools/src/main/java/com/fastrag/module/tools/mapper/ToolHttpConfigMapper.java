package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.ToolHttpConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * HTTP工具配置Mapper接口。
 *
 * <p>对应 tool_http_config 表，提供HTTP类型工具的配置信息CRUD操作，
 * 存储HTTP工具的URL、方法、Header、参数模板等配置。</p>
 */
@Mapper public interface ToolHttpConfigMapper extends BaseMapper<ToolHttpConfig> {}
