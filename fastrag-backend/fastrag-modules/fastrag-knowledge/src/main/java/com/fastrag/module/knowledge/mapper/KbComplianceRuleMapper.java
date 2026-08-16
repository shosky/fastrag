package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbComplianceRule;
import org.apache.ibatis.annotations.Mapper;
/** 知识库合规规则Mapper接口。 <p>对应 kb_compliance_rule 表，提供知识库合规规则数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbComplianceRuleMapper extends BaseMapper<KbComplianceRule> {}
