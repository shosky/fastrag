package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.Tool;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具配置Mapper接口。
 *
 * <p>对应 tool 表，提供工具实体的CRUD操作，
 * 管理系统中注册的所有工具（内置工具、知识库工具、HTTP工具等）的基本信息。</p>
 */
@Mapper public interface ToolMapper extends BaseMapper<Tool> {}
