package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppSkillBinding;
import org.apache.ibatis.annotations.Mapper;
/** 应用技能绑定Mapper接口。 <p>对应 AppSkillBinding 实体，提供应用技能绑定数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppSkillBindingMapper extends BaseMapper<AppSkillBinding> {}
