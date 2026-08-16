package com.fastrag.module.knowledge.entity;
/**
 * 知识审核流程节点实体类，对应数据库表 kb_review_node。
 *
 * <p>核心职责：
 * 表示审核模板中的一个审核节点，定义节点名称、类型、审批角色和节点配置。
 * 通过 orderNum 字段确定节点在审核流程中的执行顺序，多个节点组成完整的审核链路。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>templateId — 所属审核模板 ID</li>
 *   <li>nodeName — 节点名称</li>
 *   <li>nodeType — 节点类型（如审批、会签等）</li>
 *   <li>approverRole — 审批角色</li>
 *   <li>config — 节点配置参数，JSON 格式</li>
 *   <li>orderNum — 节点执行顺序</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_node") public class KbReviewNode {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String templateId,nodeName,nodeType,approverRole,config;
    private Integer orderNum;
    private LocalDateTime createdAt;
}
