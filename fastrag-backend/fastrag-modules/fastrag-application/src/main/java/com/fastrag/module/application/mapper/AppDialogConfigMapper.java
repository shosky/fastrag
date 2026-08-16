package com.fastrag.module.application.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.application.entity.AppDialogConfig;
import org.apache.ibatis.annotations.Mapper;
/** 应用对话配置Mapper接口。 <p>对应 AppDialogConfig 实体，提供应用对话配置数据的CRUD操作，继承MyBatis-Plus {@code BaseMapper} 获得基础增删改查能力。</p> */
@Mapper public interface AppDialogConfigMapper extends BaseMapper<AppDialogConfig> {}
