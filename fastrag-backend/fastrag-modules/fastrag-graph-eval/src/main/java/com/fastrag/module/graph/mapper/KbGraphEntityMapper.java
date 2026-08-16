package com.fastrag.module.graph.mapper;

/**
 * 知识图谱实体 Mapper 接口，对应实体 {@link KbGraphEntity}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_graph_entity} 表的基础CRUD操作。
 * 业务层通过该接口完成实体节点的插入、查询、删除等操作，支持按kbId筛选实体列表。</p>
 *
 * @see KbGraphEntity
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphEntityMapper extends BaseMapper<KbGraphEntity> {
}
