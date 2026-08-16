package com.fastrag.module.knowledge.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.knowledge.entity.KbResetConfig;
import org.apache.ibatis.annotations.Mapper;
/** 知识库重置配置Mapper接口。 <p>对应 kb_reset_config 表，提供知识库重置配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface KbResetConfigMapper extends BaseMapper<KbResetConfig> {}
