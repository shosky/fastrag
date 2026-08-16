package com.fastrag.module.graph.mapper;

/**
 * 知识图谱关系/三元组 Mapper 接口，对应实体 {@link KbGraphRelation}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_graph_relation} 表的基础CRUD操作。
 * 业务层通过该接口完成三元组关系的插入、查询、删除等操作，支持按kbId筛选关系列表。</p>
 *
 * @see KbGraphRelation
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphRelationMapper extends BaseMapper<KbGraphRelation> {
}
