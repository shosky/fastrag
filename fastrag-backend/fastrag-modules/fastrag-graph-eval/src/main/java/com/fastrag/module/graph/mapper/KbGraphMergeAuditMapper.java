package com.fastrag.module.graph.mapper;

/**
 * 同义实体合并审计 Mapper，对应实体 {@link com.fastrag.module.graph.entity.KbGraphMergeAudit}。
 *
 * <p>提供 {@code kb_graph_merge_audit} 表的基础 CRUD，供合并审计写入与回滚查询。</p>
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphMergeAudit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphMergeAuditMapper extends BaseMapper<KbGraphMergeAudit> {
}
