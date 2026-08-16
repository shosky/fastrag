package com.fastrag.module.platform.mapper;

/**
 * 模型调用日志 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供 {@link com.fastrag.module.platform.entity.ModelCallLog}
 * 实体的数据库持久化操作。对应数据库表 {@code model_call_log}。
 * 无自定义SQL，所有操作均通过 BaseMapper 提供的通用方法完成。
 * </p>
 *
 * @see com.fastrag.module.platform.entity.ModelCallLog
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.platform.entity.ModelCallLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ModelCallLogMapper extends BaseMapper<ModelCallLog> {}
