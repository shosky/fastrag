package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppGlobalPolicy;
import org.apache.ibatis.annotations.Mapper;
/** 应用全局策略Mapper接口。 <p>对应 AppGlobalPolicy 实体，提供应用全局策略数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppGlobalPolicyMapper extends BaseMapper<AppGlobalPolicy> {}
