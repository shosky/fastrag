package com.fastrag.module.graph.mapper;

/**
 * 评测任务 Mapper 接口，对应实体 {@link KbEvaluation}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_evaluation} 表的基础CRUD操作。
 * 业务层通过该接口管理评测任务的创建、状态更新和结果查询。</p>
 *
 * @see KbEvaluation
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbEvaluation;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbEvaluationMapper extends BaseMapper<KbEvaluation> {}
