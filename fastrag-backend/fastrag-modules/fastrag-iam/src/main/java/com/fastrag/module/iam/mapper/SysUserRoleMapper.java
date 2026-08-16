package com.fastrag.module.iam.mapper;

/**
 * 用户-角色关联 Mapper 接口，提供 {@link SysUserRole} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code PersonnelServiceImpl} 用于用户角色的分配和移除，
 * 在权限校验流程中用于查询用户拥有的角色列表。
 *
 * @see SysUserRole
 * @see com.fastrag.module.iam.service.impl.PersonnelServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
