package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbReviewNode;
import org.apache.ibatis.annotations.Mapper;
/** 知识库审核节点Mapper接口。 <p>对应 kb_review_node 表，提供知识库审核节点数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbReviewNodeMapper extends BaseMapper<KbReviewNode> {}
