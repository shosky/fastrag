package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.WfMigration;
import org.apache.ibatis.annotations.Mapper;
/** 工作流迁移记录Mapper接口。 <p>对应 WfMigration 实体，提供工作流迁移记录数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface WfMigrationMapper extends BaseMapper<WfMigration> {}
