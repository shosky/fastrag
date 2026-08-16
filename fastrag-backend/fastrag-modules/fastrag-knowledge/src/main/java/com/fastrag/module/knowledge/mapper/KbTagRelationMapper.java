package com.fastrag.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.knowledge.entity.KbTagRelation;
import org.apache.ibatis.annotations.Mapper;

/** 标签关联Mapper接口。 <p>对应 kb_tag_relation 表，提供标签关联数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper
public interface KbTagRelationMapper extends BaseMapper<KbTagRelation> {
}
