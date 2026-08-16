package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppOptimization;
import org.apache.ibatis.annotations.Mapper;
/** 应用优化配置Mapper接口。 <p>对应 AppOptimization 实体，提供应用优化配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppOptimizationMapper extends BaseMapper<AppOptimization> {}
