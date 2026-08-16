package com.fastrag.module.graph.mapper;

/**
 * 知识图谱基准测试 Mapper 接口，对应实体 {@link KbBenchmark}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_benchmark} 表的基础CRUD操作。
 * 业务层通过该接口完成基准测试记录的增删改查，无需编写XML映射文件。</p>
 *
 * @see KbBenchmark
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbBenchmark;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbBenchmarkMapper extends BaseMapper<KbBenchmark> {}
