package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.DbInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库实例配置Mapper接口。
 *
 * <p>对应 db_instance 表，提供数据库实例配置的CRUD操作，
 * 继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p>
 */
@Mapper public interface DbInstanceMapper extends BaseMapper<DbInstance> {}
