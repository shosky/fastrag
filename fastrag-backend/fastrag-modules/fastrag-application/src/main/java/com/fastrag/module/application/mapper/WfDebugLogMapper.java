package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.application.entity.WfDebugLog;
import org.apache.ibatis.annotations.Mapper;
/** 工作流调试日志Mapper接口。 <p>对应 WfDebugLog 实体，提供工作流调试日志数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface WfDebugLogMapper extends BaseMapper<WfDebugLog> {}
