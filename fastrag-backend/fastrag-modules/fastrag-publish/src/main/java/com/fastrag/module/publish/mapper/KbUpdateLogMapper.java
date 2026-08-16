package com.fastrag.module.publish.mapper;

/**
 * 知识库更新日志 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbUpdateLog} 实体
 * （对应数据库表 {@code kb_update_log}）的 CRUD 操作。</p>
 *
 * @see KbUpdateLog
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.publish.entity.KbUpdateLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbUpdateLogMapper extends BaseMapper<KbUpdateLog> {}
