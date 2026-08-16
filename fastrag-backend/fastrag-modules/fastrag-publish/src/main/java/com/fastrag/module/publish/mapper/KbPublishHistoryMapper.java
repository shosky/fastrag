package com.fastrag.module.publish.mapper;

/**
 * 知识库发布历史 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbPublishHistory} 实体
 * （对应数据库表 {@code kb_publish_history}）的 CRUD 操作。</p>
 *
 * @see KbPublishHistory
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.publish.entity.KbPublishHistory;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbPublishHistoryMapper extends BaseMapper<KbPublishHistory> {}
