package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.knowledge.entity.KbQaPair;
import org.apache.ibatis.annotations.Mapper;
/** 知识库问答对Mapper接口。 <p>对应 kb_qa_pair 表，提供知识库问答对数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbQaPairMapper extends BaseMapper<KbQaPair> {}
