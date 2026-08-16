package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.WfNode;
import org.apache.ibatis.annotations.Mapper;
/** 工作流节点Mapper接口。 <p>对应 WfNode 实体，提供工作流节点数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface WfNodeMapper extends BaseMapper<WfNode> {}
