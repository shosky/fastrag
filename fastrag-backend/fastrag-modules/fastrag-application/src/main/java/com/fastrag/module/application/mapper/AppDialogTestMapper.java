package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppDialogTest;
import org.apache.ibatis.annotations.Mapper;
/** 应用对话测试Mapper接口。 <p>对应 AppDialogTest 实体，提供应用对话测试数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppDialogTestMapper extends BaseMapper<AppDialogTest> {}
