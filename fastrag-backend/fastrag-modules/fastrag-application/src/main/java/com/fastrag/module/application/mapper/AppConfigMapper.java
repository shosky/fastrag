package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.application.entity.AppConfig;
import org.apache.ibatis.annotations.Mapper;
/** 应用配置Mapper接口。 <p>对应 AppConfig 实体，提供应用配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppConfigMapper extends BaseMapper<AppConfig> {}
