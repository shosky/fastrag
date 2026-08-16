package com.fastrag.module.publish.mapper;

/**
 * 知识库版本 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbVersion} 实体
 * （对应数据库表 {@code kb_version}）的 CRUD 操作。</p>
 *
 * @see KbVersion
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.publish.entity.KbVersion;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbVersionMapper extends BaseMapper<KbVersion> {}
