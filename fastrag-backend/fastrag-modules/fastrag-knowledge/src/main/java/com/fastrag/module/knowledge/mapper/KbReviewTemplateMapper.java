package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbReviewTemplate;
import org.apache.ibatis.annotations.Mapper;
/** 知识库审核模板Mapper接口。 <p>对应 kb_review_template 表，提供知识库审核模板数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbReviewTemplateMapper extends BaseMapper<KbReviewTemplate> {}
