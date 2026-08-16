package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppVariable;
import org.apache.ibatis.annotations.Mapper;
/** 应用变量Mapper接口。 <p>对应 AppVariable 实体，提供应用变量数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppVariableMapper extends BaseMapper<AppVariable> {}
