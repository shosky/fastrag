package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbPublishPlan;
import org.apache.ibatis.annotations.Mapper;
/** 知识库发布计划Mapper接口。 <p>对应 kb_publish_plan 表，提供知识库发布计划数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbPublishPlanMapper extends BaseMapper<KbPublishPlan> {}
