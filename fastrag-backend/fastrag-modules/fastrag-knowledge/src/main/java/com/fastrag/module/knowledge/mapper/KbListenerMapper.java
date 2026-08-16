package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbListener;
import org.apache.ibatis.annotations.Mapper;
/** 知识库目录监听器Mapper接口。 <p>对应 kb_listener 表，提供知识库目录监听器数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbListenerMapper extends BaseMapper<KbListener> {}
