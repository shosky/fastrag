package com.fastrag.module.operation.mapper;

/**
 * 用户反馈 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link com.fastrag.module.operation.entity.UserFeedback}
 * 实体（user_feedback 表）的基础 CRUD 操作。由 {@link com.fastrag.module.operation.service.FeedbackService} 使用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.operation.entity.UserFeedback;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {}
