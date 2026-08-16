package com.fastrag.module.knowledge.entity;
/**
 * 知识库监听器实体类，对应数据库表 kb_listener。
 *
 * <p>核心职责：
 * 表示知识库级别的事件监听器配置，用于在特定事件发生时自动触发外部通知或处理流程。
 * 监听器定义了监听类型（listenType）、目标地址（target）和配置参数（config），
 * 记录最近执行时间（lastRunAt）和运行状态。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>name — 监听器名称</li>
 *   <li>listenType — 监听的事件类型</li>
 *   <li>target — 事件触发的目标地址（如 Webhook URL）</li>
 *   <li>config — 监听器配置参数，JSON 格式</li>
 *   <li>status — 监听器状态（如 active / paused）</li>
 *   <li>createdBy — 创建者用户 ID</li>
 *   <li>lastRunAt — 最近一次执行时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_listener") public class KbListener {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,listenType,target,config,status,createdBy;
    private LocalDateTime lastRunAt,createdAt;
}
