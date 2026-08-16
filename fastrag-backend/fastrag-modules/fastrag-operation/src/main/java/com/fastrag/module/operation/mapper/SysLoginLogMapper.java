package com.fastrag.module.operation.mapper;

/**
 * 系统登录日志 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link com.fastrag.module.operation.entity.SysLoginLog}
 * 实体（sys_login_log 表）的基础 CRUD 操作。由 {@link com.fastrag.module.operation.service.SysLoginLogService} 使用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.operation.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {}
