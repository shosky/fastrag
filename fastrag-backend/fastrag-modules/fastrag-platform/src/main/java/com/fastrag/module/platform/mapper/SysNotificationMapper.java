package com.fastrag.module.platform.mapper;

/**
 * 系统通知 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供 {@link com.fastrag.module.platform.entity.SysNotification}
 * 实体的数据库持久化操作。对应数据库表 {@code sys_notification}。
 * 注意：该接口未使用 {@code @Mapper} 注解，通过 {@code @RestController} 层直接注入使用。
 * 无自定义SQL，所有操作均通过 BaseMapper 提供的通用方法完成。
 * </p>
 *
 * @see com.fastrag.module.platform.entity.SysNotification
 * @see com.fastrag.module.platform.controller.NotificationController
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.platform.entity.SysNotification;
public interface SysNotificationMapper extends BaseMapper<SysNotification> {}
