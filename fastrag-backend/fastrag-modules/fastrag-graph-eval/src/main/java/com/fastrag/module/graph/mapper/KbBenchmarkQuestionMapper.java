package com.fastrag.module.graph.mapper;

/**
 * 基准测试题目 Mapper 接口，对应实体 {@link KbBenchmarkQuestion}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_benchmark_question} 表的基础CRUD操作。
 * 业务层通过该接口管理基准测试中的题目数据。</p>
 *
 * @see KbBenchmarkQuestion
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbBenchmarkQuestionMapper extends BaseMapper<KbBenchmarkQuestion> {}
