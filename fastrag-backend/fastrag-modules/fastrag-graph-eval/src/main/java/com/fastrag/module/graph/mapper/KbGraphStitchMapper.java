package com.fastrag.module.graph.mapper;

/**
 * 图谱缝合输出 Mapper，对应实体 {@link com.fastrag.module.graph.entity.KbGraphStitch}。
 *
 * <p>提供 {@code kb_graph_stitch} 表的基础 CRUD：GraphBuildConsumer 写入缝合结果与
 * 合并候选；GraphService 读取待人工确认的合并候选。</p>
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphStitch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphStitchMapper extends BaseMapper<KbGraphStitch> {
}
