package com.fastrag.module.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent实体类，对应数据库表agent，存储Agent的持久化配置信息。
 *
 * <p>核心职责：
 * <ul>
 *   <li>持久化Agent的元数据（名称、描述、图标、slug等）</li>
 *   <li>关联Agent后端类型（backendId），决定使用哪种{@link com.fastrag.module.agent.backend.AgentBackend}实现</li>
 *   <li>存储Agent的运行时配置（configJson），包含模型选择、系统提示词、工具列表等配置</li>
 *   <li>存储分享配置（shareConfig），控制Agent的共享行为</li>
 *   <li>标记Agent是否为默认Agent和是否为子Agent</li>
 * </ul></p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>id - 主键，使用ASSIGN_ID策略（雪花算法）自动生成</li>
 *   <li>slug - URL友好的唯一标识符，用于API路径参数</li>
 *   <li>backendId - 后端类型标识，对应AgentBackend.getId()的返回值（如"ChatbotAgent"、"SubAgentBackend"）</li>
 *   <li>configJson - Agent运行时配置JSON，由{@link com.fastrag.module.agent.context.BaseContext#updateFromMap(Map)}方法读取填充到运行时上下文</li>
 *   <li>isDefault - 是否为默认Agent，系统中同一时间只有一个默认Agent</li>
 *   <li>isSubagent - 是否为子Agent（子Agent由主Agent运行时动态创建，不直接面向用户）</li>
 * </ul></p>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>通过AgentMapper（MyBatis-Plus）进行数据库CRUD操作</li>
 *   <li>configJson字段通过JacksonTypeHandler在JSON与Map之间自动转换</li>
 *   <li>由AgentService负责业务逻辑，由ContextBuilder读取configJson构建运行时上下文</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.mapper.AgentMapper 数据库映射器
 * @see com.fastrag.module.agent.service.AgentService 业务服务
 */
@Data
@TableName(value = "agent", autoResultMap = true)
public class Agent {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String slug;

    private String name;

    private String backendId;

    private String description;

    private String icon;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> pics;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> shareConfig;

    private Boolean isDefault;

    private Boolean isSubagent;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
