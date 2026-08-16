package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppToolBinding;
import org.apache.ibatis.annotations.Mapper;
/** 应用工具绑定Mapper接口。 <p>对应 AppToolBinding 实体，提供应用工具绑定数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppToolBindingMapper extends BaseMapper<AppToolBinding> {}
