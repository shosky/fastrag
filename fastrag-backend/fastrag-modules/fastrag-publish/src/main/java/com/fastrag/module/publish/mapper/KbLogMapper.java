package com.fastrag.module.publish.mapper;

/**
 * 知识库操作日志 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbLog} 实体
 * （对应数据库表 {@code kb_log}）的 CRUD 操作。继承 BaseMapper 即可获得
 * 标准的增删改查能力，无需自定义 SQL。</p>
 *
 * @see KbLog
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.publish.entity.KbLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbLogMapper extends BaseMapper<KbLog> {}
