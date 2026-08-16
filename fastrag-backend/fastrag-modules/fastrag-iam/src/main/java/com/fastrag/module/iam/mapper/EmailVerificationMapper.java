package com.fastrag.module.iam.mapper;

/**
 * 邮箱验证码 Mapper 接口，提供 {@link EmailVerification} 实体的数据库访问能力。
 *
 * <p>继承 MyBatis-Plus 的 {@code BaseMapper}，自动具备基本的 CRUD 操作。
 * 主要由 {@code AuthServiceImpl} 用于验证码的创建、查询和状态更新。
 *
 * @see EmailVerification
 * @see com.fastrag.module.iam.service.impl.AuthServiceImpl
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.iam.entity.EmailVerification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailVerificationMapper extends BaseMapper<EmailVerification> {
}
