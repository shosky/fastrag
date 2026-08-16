package com.fastrag.module.graph.mapper;

/**
 * 评测结果明细 Mapper 接口，对应实体 {@link KbEvaluationResult}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_evaluation_result} 表的基础CRUD操作。
 * 业务层通过该接口记录和查询每道评测题的执行结果。</p>
 *
 * @see KbEvaluationResult
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbEvaluationResult;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbEvaluationResultMapper extends BaseMapper<KbEvaluationResult> {}
