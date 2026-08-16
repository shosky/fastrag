package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbQualityRule;
import org.apache.ibatis.annotations.Mapper;
/** 知识库质量规则Mapper接口。 <p>对应 kb_quality_rule 表，提供知识库质量规则数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbQualityRuleMapper extends BaseMapper<KbQualityRule> {}
