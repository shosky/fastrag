package com.fastrag.module.graph.mapper;

/**
 * 三元组提及追踪 Mapper 接口，对应实体 {@link KbGraphTripleMention}。
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供对 {@code kb_graph_triple_mention} 表的基础CRUD操作。
 * 业务层通过该接口管理三元组与文档chunk之间的引用关系，支持孤儿检测和增量构建时的定位查询。</p>
 *
 * @see KbGraphTripleMention
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.graph.entity.KbGraphTripleMention;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGraphTripleMentionMapper extends BaseMapper<KbGraphTripleMention> {
}
