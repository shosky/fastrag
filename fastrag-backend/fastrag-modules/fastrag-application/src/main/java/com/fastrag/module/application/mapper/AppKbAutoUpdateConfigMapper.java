package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.application.entity.AppKbAutoUpdateConfig;
import org.apache.ibatis.annotations.Mapper;
/** 应用知识库自动更新配置Mapper接口。 <p>对应 AppKbAutoUpdateConfig 实体，提供应用知识库自动更新配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppKbAutoUpdateConfigMapper extends BaseMapper<AppKbAutoUpdateConfig> {}
