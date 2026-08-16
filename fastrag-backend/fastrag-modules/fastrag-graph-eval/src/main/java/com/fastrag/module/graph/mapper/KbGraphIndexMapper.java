package com.fastrag.module.graph.mapper;

/**
 * 知识图谱构建索引状态 Mapper 接口，对应实体 {@link KbGraphIndex}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_graph_index} 表的基础CRUD操作。
 * 业务层通过该接口查询和更新知识库的图谱构建状态、进度信息和配置参数。
 * 由于kbId作为主键，每个知识库的索引记录唯一。</p>
 *
 * @see KbGraphIndex
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphIndex;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbGraphIndexMapper extends BaseMapper<KbGraphIndex> {}
