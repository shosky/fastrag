package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.DbTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库表配置Mapper接口。
 *
 * <p>对应 db_table 表，提供数据库表配置的CRUD操作，
 * 记录数据库实例下的表结构信息，用于Agent的数据库工具查询。</p>
 */
@Mapper public interface DbTableMapper extends BaseMapper<DbTable> {}
