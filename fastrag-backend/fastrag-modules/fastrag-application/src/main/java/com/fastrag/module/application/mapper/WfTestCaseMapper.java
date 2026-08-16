package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.WfTestCase;
import org.apache.ibatis.annotations.Mapper;
/** 工作流测试用例Mapper接口。 <p>对应 WfTestCase 实体，提供工作流测试用例数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface WfTestCaseMapper extends BaseMapper<WfTestCase> {}
