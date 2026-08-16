package com.fastrag.module.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.knowledge.entity.KbCategory;

/**
 * 知识库分类Mapper接口。
 *
 * <p>对应 kb_category 表，提供知识库分类数据的CRUD操作，
 * 继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p>
 */
public interface KbCategoryMapper extends BaseMapper<KbCategory> {}
