package com.fastrag.module.iam.mapper;

/**
 * 角色-权限关联 Mapper 接口，提供 {@link SysRolePermission} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code RoleServiceImpl} 和 {@code PermissionServiceImpl} 用于
 * 角色与权限的绑定/解绑操作。
 *
 * @see SysRolePermission
 * @see com.fastrag.module.iam.service.impl.RoleServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {}
