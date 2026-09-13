package com.fastrag.module.graph.mapper;

/**
 * 图谱抽取单元 Mapper，对应实体 {@link com.fastrag.module.graph.entity.KbGraphUnit}。
 *
 * <p>提供 {@code kb_graph_unit} 表的基础 CRUD：单元级缓存命中判断、抽取结果持久化与 replay 重放。</p>
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphUnit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphUnitMapper extends BaseMapper<KbGraphUnit> {
}
