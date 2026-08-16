package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppPublishRecord;
import org.apache.ibatis.annotations.Mapper;
/** 应用发布记录Mapper接口。 <p>对应 AppPublishRecord 实体，提供应用发布记录数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppPublishRecordMapper extends BaseMapper<AppPublishRecord> {}
