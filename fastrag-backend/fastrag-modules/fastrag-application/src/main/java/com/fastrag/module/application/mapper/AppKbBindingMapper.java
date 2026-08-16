package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppKbBinding;
import org.apache.ibatis.annotations.Mapper;
/** 应用知识库绑定Mapper接口。 <p>对应 AppKbBinding 实体，提供应用知识库绑定数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppKbBindingMapper extends BaseMapper<AppKbBinding> {}
