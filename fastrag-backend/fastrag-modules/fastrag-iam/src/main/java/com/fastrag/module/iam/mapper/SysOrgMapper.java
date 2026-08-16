package com.fastrag.module.iam.mapper;

/**
 * 组织架构 Mapper 接口，提供 {@link SysOrg} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code OrgServiceImpl} 用于组织树的构建、组织节点的增删改查。
 *
 * @see SysOrg
 * @see com.fastrag.module.iam.service.impl.OrgServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysOrg;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysOrgMapper extends BaseMapper<SysOrg> {}
