package com.fastrag.module.iam.mapper;

/**
 * 系统权限 Mapper 接口，提供 {@link SysPermission} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code PermissionServiceImpl} 用于权限项的查询、创建、更新和删除，
 * 以及权限树形结构的构建。
 *
 * @see SysPermission
 * @see com.fastrag.module.iam.service.impl.PermissionServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysPermissionMapper extends BaseMapper<SysPermission> {}
