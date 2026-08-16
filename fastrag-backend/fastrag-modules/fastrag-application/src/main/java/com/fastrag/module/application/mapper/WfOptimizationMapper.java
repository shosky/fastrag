package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.WfOptimization;
import org.apache.ibatis.annotations.Mapper;
/** 工作流优化配置Mapper接口。 <p>对应 WfOptimization 实体，提供工作流优化配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface WfOptimizationMapper extends BaseMapper<WfOptimization> {}
