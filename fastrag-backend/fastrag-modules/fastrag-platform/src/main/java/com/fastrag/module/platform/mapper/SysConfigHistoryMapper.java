package com.fastrag.module.platform.mapper;

/**
 * 系统配置变更历史 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供 {@link com.fastrag.module.platform.entity.SysConfigHistory}
 * 实体的数据库持久化操作。对应数据库表 {@code sys_config_history}。
 * 无自定义SQL，所有操作均通过 BaseMapper 提供的通用方法完成。
 * </p>
 *
 * @see com.fastrag.module.platform.entity.SysConfigHistory
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.platform.entity.SysConfigHistory;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysConfigHistoryMapper extends BaseMapper<SysConfigHistory> {}
