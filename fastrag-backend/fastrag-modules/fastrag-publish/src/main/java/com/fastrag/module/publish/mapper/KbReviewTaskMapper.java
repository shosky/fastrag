package com.fastrag.module.publish.mapper;

/**
 * 知识库审核任务 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbReviewTask} 实体
 * （对应数据库表 {@code kb_review_task}）的 CRUD 操作。</p>
 *
 * @see KbReviewTask
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.publish.entity.KbReviewTask;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbReviewTaskMapper extends BaseMapper<KbReviewTask> {}
