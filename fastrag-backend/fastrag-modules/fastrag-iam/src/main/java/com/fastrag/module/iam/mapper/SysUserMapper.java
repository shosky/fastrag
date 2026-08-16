package com.fastrag.module.iam.mapper;

/**
 * 系统用户 Mapper 接口，提供 {@link SysUser} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 是 IAM 模块最核心的 Mapper，被 {@code AuthServiceImpl}、{@code PersonnelServiceImpl}
 * 等多个 Service 实现类依赖，用于用户信息的查询、创建和更新。
 *
 * @see SysUser
 * @see com.fastrag.module.iam.service.impl.AuthServiceImpl
 * @see com.fastrag.module.iam.service.impl.PersonnelServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysUserMapper extends BaseMapper<SysUser> {}
