package com.fastrag.module.iam.mapper;

/**
 * 知识库访问控制 Mapper 接口，提供 {@link KbAcl} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code KbAclServiceImpl} 用于知识库 ACL 规则的查询、添加和删除。
 *
 * @see KbAcl
 * @see com.fastrag.module.iam.service.impl.KbAclServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.KbAcl;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbAclMapper extends BaseMapper<KbAcl> {}
