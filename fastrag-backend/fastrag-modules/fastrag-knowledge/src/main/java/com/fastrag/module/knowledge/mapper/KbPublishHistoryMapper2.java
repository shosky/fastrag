package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbPublishHistory;
import org.apache.ibatis.annotations.Mapper;
/** 知识库发布历史Mapper接口。 <p>对应 kb_publish_history 表，提供知识库发布历史数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbPublishHistoryMapper2 extends BaseMapper<KbPublishHistory> {}
