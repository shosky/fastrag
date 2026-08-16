package com.fastrag.module.knowledge.entity;
/**
 * 知识发布历史实体类，对应数据库表 kb_publish_history。
 *
 * <p>核心职责：
 * 记录知识库中知识条目的每一次发布和撤销操作，形成完整的发布变更历史。
 * 每条记录包含线上版本号（onlineVersion）和线下版本号（offlineVersion），
 * 用于版本对比和回溯。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>knowledgeId — 发布的知识条目 ID</li>
 *   <li>publishType — 发布类型（如 publish / revoke）</li>
 *   <li>onlineVersion / offlineVersion — 线上和线下版本号</li>
 *   <li>status — 发布状态</li>
 *   <li>operator — 操作者用户 ID</li>
 *   <li>version — 版本序号</li>
 *   <li>scheduledAt — 计划发布时间</li>
 *   <li>publishedAt — 实际发布时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_publish_history") public class KbPublishHistory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,publishType,onlineVersion,offlineVersion,status,operator;
    private Integer version;
    private LocalDateTime scheduledAt,publishedAt,createdAt;
}
