package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppBasicConfig;
import org.apache.ibatis.annotations.Mapper;
/** 应用基础配置Mapper接口。 <p>对应 AppBasicConfig 实体，提供应用基础配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppBasicConfigMapper extends BaseMapper<AppBasicConfig> {}
