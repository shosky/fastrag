package com.fastrag.module.retrieval.mapper;

/**
 * 知识库更新提醒配置 Mapper 接口。
 *
 * <p>基于 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link KbUpdateRemind} 实体
 * （对应数据库表 {@code kb_update_remind}）的 CRUD 操作。</p>
 *
 * @see KbUpdateRemind
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.fastrag.module.retrieval.entity.KbUpdateRemind;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface KbUpdateRemindMapper extends BaseMapper<KbUpdateRemind> {}
