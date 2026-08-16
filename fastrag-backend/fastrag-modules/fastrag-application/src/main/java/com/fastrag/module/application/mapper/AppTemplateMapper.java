package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.application.entity.AppTemplate;
import org.apache.ibatis.annotations.Mapper;
/** 应用模板Mapper接口。 <p>对应 AppTemplate 实体，提供应用模板数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppTemplateMapper extends BaseMapper<AppTemplate> {}
