package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppConversationMessage;
import org.apache.ibatis.annotations.Mapper;
/** 应用会话消息Mapper接口。 <p>对应 AppConversationMessage 实体，提供应用会话消息数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppConversationMessageMapper extends BaseMapper<AppConversationMessage> {}
