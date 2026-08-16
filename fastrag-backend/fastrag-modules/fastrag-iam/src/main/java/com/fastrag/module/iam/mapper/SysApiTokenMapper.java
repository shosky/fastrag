package com.fastrag.module.iam.mapper;

/**
 * API Token Mapper 接口，提供 {@link SysApiToken} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code ApiTokenServiceImpl} 用于 Token 的创建、查询和撤销。
 *
 * @see SysApiToken
 * @see com.fastrag.module.iam.service.impl.ApiTokenServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.SysApiToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysApiTokenMapper extends BaseMapper<SysApiToken> {
}
