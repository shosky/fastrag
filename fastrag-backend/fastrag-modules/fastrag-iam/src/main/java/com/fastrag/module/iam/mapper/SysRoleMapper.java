package com.fastrag.module.iam.mapper;

/**
 * 系统角色 Mapper 接口，提供 {@link SysRole} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code RoleServiceImpl} 用于角色的查询、创建、更新、删除及默认角色设置。
 *
 * @see SysRole
 * @see com.fastrag.module.iam.service.impl.RoleServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysRoleMapper extends BaseMapper<SysRole> {}
