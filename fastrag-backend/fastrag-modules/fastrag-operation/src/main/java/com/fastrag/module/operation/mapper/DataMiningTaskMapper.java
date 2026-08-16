package com.fastrag.module.operation.mapper;

/**
 * 数据挖掘任务 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link com.fastrag.module.operation.entity.DataMiningTask}
 * 实体（data_mining_task 表）的基础 CRUD 操作。由 {@link com.fastrag.module.operation.service.DataMiningService} 使用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.operation.entity.DataMiningTask;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface DataMiningTaskMapper extends BaseMapper<DataMiningTask> {}
