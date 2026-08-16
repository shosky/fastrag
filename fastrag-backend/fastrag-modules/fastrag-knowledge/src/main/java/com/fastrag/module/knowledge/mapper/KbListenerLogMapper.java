package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbListenerLog;
import org.apache.ibatis.annotations.Mapper;
/** 知识库监听器日志Mapper接口。 <p>对应 kb_listener_log 表，提供知识库监听器日志数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbListenerLogMapper extends BaseMapper<KbListenerLog> {}
