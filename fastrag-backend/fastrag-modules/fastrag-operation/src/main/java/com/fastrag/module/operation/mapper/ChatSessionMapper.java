package com.fastrag.module.operation.mapper;

/**
 * 聊天会话 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link com.fastrag.module.operation.entity.ChatSession}
 * 实体（chat_session 表）的基础 CRUD 操作。由 {@link com.fastrag.module.operation.controller.ChatSessionController} 直接使用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.operation.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ChatSessionMapper extends BaseMapper<ChatSession> {}
