package com.fastrag.module.knowledge.entity;
/**
 * 知识重置配置实体类，对应数据库表 kb_reset_config。
 *
 * <p>核心职责：
 * 定义知识库中知识条目的重置策略配置，指定哪些角色可以执行重置操作（canReset）
 * 以及最大重置次数限制（maxResetCount）。重置会将知识恢复到指定历史配置状态。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>roleKey — 适用角色键（如 owner / editor）</li>
 *   <li>config — 重置配置参数，JSON 格式</li>
 *   <li>canReset — 是否允许该角色执行重置（0=禁止，1=允许）</li>
 *   <li>maxResetCount — 最大重置次数</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_reset_config") public class KbResetConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,roleKey,config;
    private Integer canReset,maxResetCount;
    private LocalDateTime createdAt;
}
