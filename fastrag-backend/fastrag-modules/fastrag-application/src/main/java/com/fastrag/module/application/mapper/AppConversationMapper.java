package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppConversation;
import org.apache.ibatis.annotations.Mapper;
/** 应用会话Mapper接口。 <p>对应 AppConversation 实体，提供应用会话数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppConversationMapper extends BaseMapper<AppConversation> {}
